1. **Fix timing attack vulnerability in `MqttTicketUtils.java`:**
   - Update `MqttTicketUtils.java` to use `java.security.MessageDigest.isEqual()` instead of `String.equals()` for comparing HMAC signatures to prevent timing attacks.
   - The comparison will be `MessageDigest.isEqual(expectedSignature.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8))`.
2. **Complete pre commit steps:**
   - Run tests to verify the fix works correctly. Ensure proper testing, verification, review, and reflection are done before final submission.
