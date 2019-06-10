package org.mozilla.jss.tests;

import java.util.*;

import org.mozilla.jss.*;
import org.mozilla.jss.crypto.*;
import org.mozilla.jss.pkcs11.*;
import org.mozilla.jss.pkix.*;
import org.mozilla.jss.nss.*;
import org.mozilla.jss.ssl.*;
import org.mozilla.jss.util.*;

public class TestBufferPRFD {
    public static int PR_WOULD_BLOCK_ERROR = -5998;

    public static void TestCreateClose() {
        byte[] info = {0x01, 0x02, 0x03, 0x04};
        BufferProxy left_read = Buffer.Create(10);
        BufferProxy right_read = Buffer.Create(10);

        assert(left_read != null);
        assert(right_read != null);

        PRFDProxy left = PR.NewBufferPRFD(left_read, right_read, info);
        PRFDProxy right = PR.NewBufferPRFD(right_read, left_read, info);

        assert(left != null);
        assert(right != null);

        System.err.println(PR.Write(left, info));
        assert(PR.Send(left, info, 0, 0) == 4);
        assert(PR.Send(left, info, 0, 0) == 4);
        assert(PR.Send(left, info, 0, 0) == 2);

        byte[] result = PR.Recv(right, 10, 0, 0);
        assert(result.length == 10);

        for (int i = 0; i < 10; i++) {
            assert(result[i] == info[i % info.length]);
        }

        assert(PR.Close(left) == 0);
        assert(PR.Close(right) == 0);

        Buffer.Free(left_read);
        Buffer.Free(right_read);
    }

    public synchronized static PRFDProxy Setup_NSS_Client(PRFDProxy fd, String host) {
        fd = SSL.ImportFD(null, fd);
        assert(fd != null);

        assert(SSL.ResetHandshake(fd, false) == 0);
        assert(SSL.SetURL(fd, host) == 0);

        return fd;
    }

    public synchronized static PRFDProxy Setup_NSS_Server(PRFDProxy fd, String host,
        PK11Cert cert, PK11PrivKey key) throws Exception
    {
        fd = SSL.ImportFD(null, fd);
        assert(fd != null);

        assert(SSL.ConfigSecureServer(fd, cert, key, 1) == 0);
        assert(SSL.ConfigServerSessionIDCache(1, 100, 100, null) == 0);
        assert(SSL.ResetHandshake(fd, true) == 0);
        assert(SSL.SetURL(fd, host) == 0);

        TestSSLVersionGetSet(fd);

        return fd;
    }

    public synchronized static boolean IsHandshakeFinished(PRFDProxy c_nspr, PRFDProxy s_nspr) {
        SecurityStatusResult c_result = SSL.SecurityStatus(c_nspr);
        SecurityStatusResult s_result = SSL.SecurityStatus(s_nspr);

        assert(c_result != null && s_result != null);

        return c_result.on == 1 && s_result.on == 1;
    }

    public synchronized static void TestSSLVersionGetSet(PRFDProxy s_nspr) throws Exception {
        SSLVersionRange initial = SSL.VersionRangeGet(s_nspr);
        System.out.println("Initial: (" + initial.getMinVersion() + ":" + initial.getMinEnum() + ", " + initial.getMaxVersion() + ":" + initial.getMaxEnum() + ")");

        SSLVersionRange vrange = new SSLVersionRange(SSLVersion.TLS_1_1, SSLVersion.TLS_1_3);

        assert(SSL.VersionRangeSet(s_nspr, vrange) == 0);

        SSLVersionRange actual = SSL.VersionRangeGet(s_nspr);
        System.out.println("Actual: (" + actual.getMinVersion() + ":" + actual.getMinEnum() + ", " + actual.getMaxVersion() + ":" + actual.getMaxEnum() + ")");
        assert(actual.getMinEnum() <= SSLVersion.TLS_1_2.value());
        assert(SSLVersion.TLS_1_2.value() <= actual.getMaxEnum());
    }

    public synchronized static void TestSSLHandshake(String database, String password) throws Exception {
        /* Constants */
        String host = "localhost";
        byte[] peer_info = host.getBytes();

        /* Find SSL Certificate */
        CryptoManager manager;
        CryptoManager.initialize(database);
        manager = CryptoManager.getInstance();
        manager.setPasswordCallback(new Password(password.toCharArray()));
        CryptoToken token = manager.getInternalKeyStorageToken();

        PK11Cert server_cert = (PK11Cert) manager.findCertByNickname("Server_RSA");
        PK11PrivKey server_key = (PK11PrivKey) manager.findPrivKeyByCert(server_cert);

        assert(server_cert != null);
        assert(server_cert instanceof PK11Cert);
        assert(server_key != null);
        assert(server_key instanceof PK11PrivKey);

        /* Create Buffers and BufferPRFDs */
        BufferProxy read_buf = Buffer.Create(1024);
        BufferProxy write_buf = Buffer.Create(1024);

        assert(read_buf != null);
        assert(write_buf != null);

        PRFDProxy c_nspr = PR.NewBufferPRFD(read_buf, write_buf, peer_info);
        PRFDProxy s_nspr = PR.NewBufferPRFD(write_buf, read_buf, peer_info);

        assert(c_nspr != null);
        assert(s_nspr != null);

        c_nspr = Setup_NSS_Client(c_nspr, host);
        s_nspr = Setup_NSS_Server(s_nspr, host, server_cert, server_key);

        assert(c_nspr != null);
        assert(s_nspr != null);

        assert(!IsHandshakeFinished(c_nspr, s_nspr));

        /* Try a handshake */
        while(!IsHandshakeFinished(c_nspr, s_nspr)) {
            if (SSL.ForceHandshake(c_nspr) != 0) {
                int error = PR.GetError();

                if (error != PR_WOULD_BLOCK_ERROR) {
                    System.out.println("Unexpected error: " + error);
                    System.exit(1);
                }
            }
            if (SSL.ForceHandshake(s_nspr) != 0) {
                int error = PR.GetError();

                if (error != PR_WOULD_BLOCK_ERROR) {
                    System.out.println("Unexpected error: " + error);
                    System.exit(1);
                }
            }
        }
        System.out.println("Handshake completed successfully!\n");
        assert(IsHandshakeFinished(c_nspr, s_nspr));

        /* Send data from client -> server */
        byte[] client_message = "Cooking MCs".getBytes();

        assert(PR.Write(c_nspr, client_message) == client_message.length);
        byte[] server_received = PR.Read(s_nspr, client_message.length);
        assert(server_received != null);

        if (server_received.length != client_message.length) {
            System.out.println("Expected a client message of length " + client_message.length + " but got one of " + server_received.length);
            System.exit(1);
        }

        for (int i = 0; i < client_message.length && i < server_received.length; i++) {
            if (client_message[i] != server_received[i]) {
                System.out.println("Received byte " + server_received[i] + " on server but expected " + client_message[i]);
                System.exit(1);
            }
        }

        /* Send data from server -> client */
        byte[] server_message = "like a pound of bacon".getBytes();

        assert(PR.Write(s_nspr, server_message) == server_message.length);
        byte[] client_received = PR.Read(c_nspr, server_message.length);
        assert(client_received != null);

        if (client_received.length != server_message.length) {
            System.out.println("Expected a server message of length " + server_message.length + " but got one of " + client_received.length);
            System.exit(1);
        }

        for (int i = 0; i < server_message.length && i < client_received.length; i++) {
            if (server_message[i] != client_received[i]) {
                System.out.println("Received byte " + client_received[i] + " on client but expected " + server_message[i]);
                System.exit(1);
            }
        }

        /* Close connections */
        assert(PR.Shutdown(c_nspr, PR.SHUTDOWN_BOTH) == 0);
        assert(PR.Shutdown(s_nspr, PR.SHUTDOWN_BOTH) == 0);

        /* Clean up */
        assert(PR.Close(c_nspr) == 0);
        assert(PR.Close(s_nspr) == 0);

        Buffer.Free(read_buf);
        Buffer.Free(write_buf);
    }

    public static void main(String[] args) throws Exception {
        System.loadLibrary("jss4");

        System.out.println("Calling TestCreateClose()...");
        TestCreateClose();

        System.out.println("Calling TestSSLHandshake()...");
        TestSSLHandshake(args[0], args[1]);
    }
}
