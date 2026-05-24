#!/usr/bin/env python3
"""
Generate the passphrase-encrypted "family blob" for wispr-fox-android.

Your API keys NEVER leave this machine and are NOT printed. Output is a base64
blob that is safe to commit / ship — it's useless without the passphrase.

Usage:
    pip install cryptography
    python tools/make_family_blob.py

You'll be prompted for your Groq key, (optional) Gemini key, and a passphrase.
Paste the printed value into keys.properties as:
    familyBlob=<the base64 string>
then build. In the app's onboarding, "Have a setup code?" accepts the passphrase.

Blob layout (must match FamilyUnlock.kt): salt[16] || iv[12] || ciphertext+tag,
AES-256-GCM, key = PBKDF2-HMAC-SHA256(passphrase, salt, 120000, 32 bytes).
"""

import base64
import getpass
import json
import os

from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from cryptography.hazmat.primitives.kdf.pbkdf2 import PBKDF2HMAC
from cryptography.hazmat.primitives import hashes

ITERATIONS = 120_000
KEY_LEN = 32      # 256-bit
SALT_LEN = 16
IV_LEN = 12

def main() -> None:
    groq = getpass.getpass("Groq API key (hidden): ").strip()
    gemini = getpass.getpass("Gemini API key (optional, hidden): ").strip()
    passphrase = getpass.getpass("Setup passphrase to share with family: ").strip()
    if not groq or not passphrase:
        raise SystemExit("Groq key and passphrase are required.")

    payload = json.dumps({"groq": groq, "gemini": gemini}).encode("utf-8")

    salt = os.urandom(SALT_LEN)
    iv = os.urandom(IV_LEN)
    key = PBKDF2HMAC(algorithm=hashes.SHA256(), length=KEY_LEN, salt=salt,
                     iterations=ITERATIONS).derive(passphrase.encode("utf-8"))
    ciphertext = AESGCM(key).encrypt(iv, payload, None)  # appends 16-byte tag

    blob = base64.b64encode(salt + iv + ciphertext).decode("ascii")
    print("\nAdd this line to keys.properties:\n")
    print(f"familyBlob={blob}\n")

if __name__ == "__main__":
    main()
