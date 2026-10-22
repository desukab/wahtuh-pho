package com.wahtuh.pho.text;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Package and field policy for apps where dictation must stand down. */
public final class SensitiveApps {
    private static final Set<String> EXACT = new HashSet<>(Arrays.asList(
            "com.1password",
            "com.agilebits.onepassword",
            "com.onepassword.android",
            "com.bitwarden",
            "com.lastpass.lpandroid",
            "com.dashlane",
            "com.keepersecurity.keeper",
            "com.enpass",
            "com.nordpass.android",
            "com.roboform",
            "com.keepassdroid",
            "com.google.android.apps.authenticator2",
            "com.azure.authenticator",
            "com.authy.authy",
            "com.duosecurity.duo.mobilepass",
            "com.chase.sig.android",
            "com.wf.wellsfargomobile",
            "com.bankofamerica.cashpromobile",
            "com.citi.citimobile",
            "com.americanexpress.android.acctsvcs.us",
            "com.discoverfinancial.mobile",
            "com.capitalone.mobile",
            "com.usbank.mobilebanking",
            "com.pnc.ecommerce.mobile",
            "com.samsung.android.spay",
            "com.squareup.cash",
            "com.venmo",
            "com.paypal.android.p2pmobile",
            "com.google.android.apps.walletnfcrel"
    ));

    private static final Set<String> SEGMENTS = new HashSet<>(Arrays.asList(
            "bank", "banking", "wallet", "pay", "payment", "payments",
            "finance", "vault", "password", "passwords", "authenticator",
            "otp", "2fa", "crypto", "invest", "investing", "brokerage",
            "trading", "insurance", "tax"
    ));

    private SensitiveApps() {}

    public static boolean blocks(String packageName) {
        if (packageName == null) return false;
        String p = packageName.toLowerCase(Locale.US);
        if (p.isEmpty() || p.equals("com.wahtuh.pho")) return false;
        if (EXACT.contains(p)) return true;
        for (String part : p.split("\\.")) {
            if (SEGMENTS.contains(part)) return true;
        }
        return p.contains("paypal") || p.contains("banking") || p.contains("bankof")
                || p.contains("wellsfargo") || p.contains("citibank")
                || p.contains("capitalone") || p.contains("americanexpress")
                || p.contains("discoverfinancial") || p.contains("authenticator")
                || p.contains("1password") || p.contains("onepassword")
                || p.contains("bitwarden") || p.contains("lastpass")
                || p.contains("dashlane") || p.contains("nordpass")
                || p.contains("keepass") || p.contains("roboform")
                || p.contains("enpass") || p.contains("passwordmanager")
                || p.contains("password");
    }
}
