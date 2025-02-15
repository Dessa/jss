/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

/* These headers must be included before this header:
#include <secoidt.h>
#include <pkcs11t.h>
#include <jni.h>
#include <Policy.h>
*/

#ifndef JSS_ALGORITHM_H
#define JSS_ALGORITHM_H

PR_BEGIN_EXTERN_C

typedef enum JSS_AlgType {
    PK11_MECH,      /* CK_MECHANISM_TYPE    */
    SEC_OID_TAG     /* SECOidTag            */
} JSS_AlgType;

typedef struct JSS_AlgInfoStr {
    unsigned long val; /* either a CK_MECHANISM_TYPE or a SECOidTag */
    JSS_AlgType type;
} JSS_AlgInfo;

#define NUM_ALGS 70

extern JSS_AlgInfo JSS_AlgTable[];
extern CK_ULONG JSS_symkeyUsage[];

/***********************************************************************
 *
 * J S S _ g e t O i d T a g F r o m A l g
 *
 * INPUTS
 *      alg
 *          An org.mozilla.jss.Algorithm object. Must not be NULL.
 * RETURNS
 *      SECOidTag corresponding to this algorithm, or SEC_OID_UNKNOWN
 *      if none was found.
 */
SECOidTag
JSS_getOidTagFromAlg(JNIEnv *env, jobject alg);

/***********************************************************************
 *
 * J S S _ g e t P K 1 1 M e c h F r o m A l g
 *
 * INPUTS
 *      alg
 *          An org.mozilla.jss.Algorithm object. Must not be NULL.
 * RETURNS
 *          CK_MECHANISM_TYPE corresponding to this algorithm, or
 *          CKM_INVALID_MECHANISM if none was found.
 */
CK_MECHANISM_TYPE
JSS_getPK11MechFromAlg(JNIEnv *env, jobject alg);

// The following are put here because NSS has not defined these yet
#ifndef CKM_AES_KEY_WRAP
#define CKM_AES_KEY_WRAP 0x2109
#endif
#ifndef CKM_AES_KEY_WRAP_PAD
#define CKM_AES_KEY_WRAP_PAD 0x210a
#endif
#ifndef CKM_AES_KEY_WRAP_KWP
#define CKM_AES_KEY_WRAP_KWP 0x210b
#endif

PR_END_EXTERN_C

#endif
