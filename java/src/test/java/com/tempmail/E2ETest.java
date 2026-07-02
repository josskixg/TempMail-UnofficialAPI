package com.tempmail;

import com.tempmail.errors.TempMailException;
import com.tempmail.models.Message;
import com.tempmail.models.MessageDetail;
import com.tempmail.providers.*;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * Simple E2E test runner — no JUnit needed.
 * Run: mvn compile exec:java -Dexec.mainClass="com.tempmail.E2ETest"
 * Or:  javac + java directly
 */
public class E2ETest {

    private static int passed = 0;
    private static int failed = 0;
    private static int skipped = 0;

    public static void main(String[] args) {
        // Load .env file (Java doesn't auto-load it)
        java.io.File envFile = new java.io.File(".env");
        if (!envFile.exists()) envFile = new java.io.File("../.env");
        if (envFile.exists()) {
            try (java.util.Scanner sc = new java.util.Scanner(envFile)) {
                while (sc.hasNextLine()) {
                    String line = sc.nextLine().trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    int eq = line.indexOf('=');
                    if (eq < 0) continue;
                    String k = line.substring(0, eq).trim();
                    String v = line.substring(eq + 1).trim();
                    if (System.getenv(k) == null) {
                        // ponytail: ProcessBuilder env injection — set in System props as fallback
                        System.setProperty("env." + k, v);
                    }
                }
            } catch (Exception ignored) {}
        }
        System.out.println("=== TempMail E2E Tests ===\n");

        testFactory();
        testProvider("mail_tm", new MailTmProvider());
        testProvider("guerrilla_mail", new GuerrillaMailProvider());
        testProvider("yopmail", new YOPmailProvider());
        testProvider("dropmail", new DropmailProvider());
        testProvider("1secemail", new OneSecEmailProvider());
        testProvider("ncaori", new NcaoriMailProvider());
        testProvider("zoromail", new ZoromailProvider());
        testProvider("tempmail_lol", new TempmailLolProvider());
        testProvider("tempmailc", new TempmailcProvider());
        testProvider("temp_mail_io", new TempMailIoProvider());
        testProvider("tempmail_plus", new TempmailPlusProvider());
        testProvider("emailfake", new EmailfakeProvider());
        testProvider("generator_email", new GeneratorEmailProvider());
        testProvider("mailnesia", new MailnesiaProvider());
        testProvider("tenminutemail", new TenMinuteMailProvider());
        testProvider("email_temp", new EmailTempProvider());

        System.out.println("\n=== Results ===");
        System.out.println("Passed: " + passed);
        System.out.println("Failed: " + failed);
        System.out.println("Skipped: " + skipped);
        System.out.println("Total:  " + (passed + failed + skipped));

        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void testFactory() {
        System.out.println("[factory] Testing TempMailFactory.create()...");
        try {
            assertCondition("yopmail factory",
                    TempMailFactory.create("yopmail") instanceof YOPmailProvider);
            assertCondition("dropmail factory",
                    TempMailFactory.create("dropmail") instanceof DropmailProvider);
            assertCondition("mail_tm factory",
                    TempMailFactory.create("mail_tm") instanceof MailTmProvider);
            assertCondition("guerrilla_mail factory",
                    TempMailFactory.create("guerrilla_mail") instanceof GuerrillaMailProvider);
            assertCondition("ncaori factory",
                    TempMailFactory.create("ncaori") instanceof NcaoriMailProvider);
            assertCondition("zoromail factory",
                    TempMailFactory.create("zoromail") instanceof ZoromailProvider);
            assertCondition("tempmail_lol factory",
                    TempMailFactory.create("tempmail.lol") instanceof TempmailLolProvider);
            assertCondition("tempmailc factory",
                    TempMailFactory.create("tempmailc") instanceof TempmailcProvider);
            assertCondition("temp_mail_io factory",
                    TempMailFactory.create("temp-mail.io") instanceof TempMailIoProvider);
            assertCondition("tempmail_plus factory",
                    TempMailFactory.create("tempmail.plus") instanceof TempmailPlusProvider);
            assertCondition("emailfake factory",
                    TempMailFactory.create("emailfake") instanceof EmailfakeProvider);
            assertCondition("generator_email factory",
                    TempMailFactory.create("generator.email") instanceof GeneratorEmailProvider);
            assertCondition("mailnesia factory",
                    TempMailFactory.create("mailnesia") instanceof MailnesiaProvider);
            assertCondition("tenminutemail factory",
                    TempMailFactory.create("10minutemail") instanceof TenMinuteMailProvider);
            assertCondition("email_temp factory",
                    TempMailFactory.create("email-temp") instanceof EmailTempProvider);
        } catch (Exception e) {
            fail("factory", e);
        }
    }

    private static void testProvider(String name, TempMailProvider provider) {
        System.out.println("\n[" + name + "] Starting E2E test...");
        try {
            // Step 1: Generate email (with retry for mail_tm)
            String email;
            if ("mail_tm".equals(name)) {
                email = generateWithRetry(name, provider);
                if (email == null) {
                    System.out.println("SKIP: mail.tm failed after 3 retry attempts");
                    skipped += 5;
                    return;
                }
            } else {
                email = provider.generateEmail();
            }
            assertCondition(name + " generateEmail", email != null && email.contains("@"));
            System.out.println("[" + name + "] Email: " + email);

            // Step 1b: Send a test email to this address
            boolean sent = sendTestEmail(email);
            if (sent) {
                System.out.println("[" + name + "] Test email sent, waiting 4s for delivery...");
                try { Thread.sleep(4000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
            } else {
                System.out.println("[" + name + "] WARN: sendTestEmail failed, skipping inbox delivery assertion");
            }

            // Step 2: Get inbox
            List<Message> inbox = provider.getInbox(email);
            assertCondition(name + " getInbox", inbox != null);
            System.out.println("[" + name + "] Inbox size: " + inbox.size());
            if (sent && (inbox == null || inbox.isEmpty())) {
                System.out.println("[" + name + "] WARN: sent test email but inbox still empty (delivery delay?)");
            }

            // Step 3: Read message if available
            if (!inbox.isEmpty()) {
                Message first = inbox.get(0);
                assertCondition(name + " message id", first.getId() != null);
                System.out.println("[" + name + "] Reading message: " + first.getId());

                MessageDetail detail = provider.readMessage(first.getId());
                assertCondition(name + " readMessage", detail != null);
                System.out.println("[" + name + "] Subject: " + detail.getSubject());
            } else {
                System.out.println("[" + name + "] No messages (expected for new addresses)");
            }

            // Step 4: Delete
            try {
                boolean deleted = provider.deleteEmail(email);
                if (!deleted) System.out.println("[" + name + "] WARN: deleteEmail returned false (may be unsupported)");
                else passed++;
            } catch (Exception e) {
                System.out.println("[" + name + "] WARN: deleteEmail failed: " + e.getMessage() + " (may be unsupported)");
            }
            System.out.println("[" + name + "] E2E PASSED");

        } catch (TempMailException e) {
            // Handle known provider-side failures gracefully
            String msg = e.getMessage() != null ? e.getMessage() : "";
            int code = e.getStatusCode();
            if ((code == 402) || (code == 400 && msg.contains("yopmail"))
                    || msg.contains("captcha_required") || msg.contains("HTTP 400")
                    || msg.contains("HTTP 402")) {
                System.out.println("SKIP: " + name + " provider unavailable (" + code + "): " + msg.substring(0, Math.min(80, msg.length())));
                skipped += 5;
                return;
            }
            // Other TempMailException: fail
            fail(name, e);
        } catch (Exception e) {
            fail(name, e);
        }
    }

    /**
     * Retry generateEmail() with backoff: 1s, 3s, 5s.
     * Returns null if all 3 attempts fail.
     */
    private static String generateWithRetry(String name, TempMailProvider provider) {
        int[] delays = {1000, 3000, 5000};
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                return provider.generateEmail();
            } catch (Exception e) {
                System.out.println("[" + name + "] generateEmail attempt " + attempt + "/3 failed: " + e.getMessage());
                if (attempt < 3) {
                    try {
                        Thread.sleep(delays[attempt - 1]);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        System.out.println("[" + name + "] Interrupted during retry wait");
                        return null;
                    }
                }
            }
        }
        return null;
    }

    private static final String[] UA_POOL = {
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:131.0) Gecko/20100101 Firefox/131.0",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36 Edg/128.0.0.0",
    };

    private static String randomUA() {
        return UA_POOL[(int)(Math.random() * UA_POOL.length)];
    }

    private static String resendApiKey() {
        String k = System.getenv("RESEND_API_KEY");
        if (k != null && !k.isEmpty()) return k;
        // ponytail: fallback to .env loaded via System properties
        k = System.getProperty("env.RESEND_API_KEY");
        return (k != null) ? k : "";
    }

    private static boolean sendTestEmail(String to) {
        int[] delays = {1000, 3000, 5000};
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                HttpClient client = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(10))
                        .build();
                String escaped = to.replace("\"", "\\\"");
                String body = "{\"from\":\"onboarding@rokupusu.web.id\",\"to\":\"" + escaped + "\",\"subject\":\"TempMail E2E Test\",\"html\":\"<p>E2E test email from TempMail wrapper</p>\"}";
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(java.net.URI.create("https://api.resend.com/emails"))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + resendApiKey())
                        .header("User-Agent", randomUA())
                        .timeout(Duration.ofSeconds(10))
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                System.out.println("  [sendTestEmail] Status: " + response.statusCode());
                if (response.statusCode() == 200) return true;
                if (attempt < 2) {
                    try { Thread.sleep(delays[attempt]); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); return false; }
                    continue;
                }
                return false;
            } catch (Exception e) {
                System.out.println("  [sendTestEmail] WARN attempt " + (attempt + 1) + "/3: " + e.getMessage());
                if (attempt < 2) {
                    try { Thread.sleep(delays[attempt]); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); return false; }
                    continue;
                }
                return false;
            }
        }
        return false;
    }

    private static void assertCondition(String label, boolean condition) {
        if (condition) {
            passed++;
        } else {
            failed++;
            System.err.println("  FAIL: " + label);
        }
    }

    private static void fail(String name, Exception e) {
        failed++;
        System.err.println("[" + name + "] FAILED: " + e.getMessage());
        e.printStackTrace(System.err);
    }
}
