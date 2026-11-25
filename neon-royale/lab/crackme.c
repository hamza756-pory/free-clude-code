/*
 * Training crackme — written for this repository's RE lab. Not real DRM.
 *
 * Build:  gcc -O1 -o crackme crackme.c
 * Play:   ./crackme
 *
 * Stage 1 (obvious):  strings ./crackme   -> you will see a hint string
 * Stage 2 (static):   objdump -d ./crackme | less   -> find check_key()
 * Stage 3 (dynamic):  gdb ./crackme  -> break check_key, inspect the argument
 * Stage 4 (defence):  read the "HARDENING NOTES" at the bottom and fix them
 *
 * The exercise is to recover the key WITHOUT reading this source first.
 * The key is intentionally derived, not stored, so `strings` only gives a hint.
 */
#include <stdio.h>
#include <string.h>
#include <stdint.h>

#define KEY_LEN 12

static const char *HINT = "hint: the key is 12 chars, digits+letters, checksum-locked";

/* djb2-ish rolling hash — the actual gate. */
static uint32_t hash_key(const char *s, size_t n) {
    uint32_t h = 5381u;
    for (size_t i = 0; i < n; i++) {
        h = ((h << 5) + h) + (unsigned char)s[i];
    }
    return h;
}

/* Secondary constraint: position-weighted sum, so brute force is narrower. */
static int shape_ok(const char *s, size_t n) {
    int acc = 0;
    for (size_t i = 0; i < n; i++) {
        acc += (int)(unsigned char)s[i] * (int)(i + 3);
    }
    return (acc % 1009) == 731;
}

/*
 * The target hash. Recover the key by inverting hash_key() with the shape
 * constraint, or by breaking here in a debugger and watching the comparison.
 */
static int check_key(const char *s) {
    size_t n = strlen(s);
    if (n != KEY_LEN) return 0;
    if (!shape_ok(s, n)) return 0;
    return hash_key(s, n) == 0x0d0e01f6u;
}

int main(void) {
    char buf[128];
    puts("=== NEON ROYALE TRAINING CRACKME ===");
    puts(HINT);
    printf("key> ");
    fflush(stdout);
    if (!fgets(buf, sizeof buf, stdin)) return 1;
    buf[strcspn(buf, "\n")] = '\0';
    if (check_key(buf)) {
        puts("[+] access granted — you reversed it");
        return 0;
    }
    puts("[-] access denied");
    return 1;
}

/*
 * HARDENING NOTES (defender's side — do this after you solve it):
 *  1. A pure client-side check is always bypassable: patch the branch or hook
 *     the function. Move the decision to a server you control.
 *  2. Never ship the secret or a trivially invertible hash of it.
 *  3. Compare with a constant-time routine if secrets are involved.
 *  4. Add rate limiting so brute force is not free.
 *  5. Log and alert on repeated failures — detection beats prevention.
 */
