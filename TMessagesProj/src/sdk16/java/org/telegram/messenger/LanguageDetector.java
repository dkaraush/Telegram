package org.telegram.messenger;

public class LanguageDetector {
    public interface StringCallback {
        void run(String str);
    }
    public interface ExceptionCallback {
        void run(Exception e);
    }

    public static boolean hasSupport() {
        return false;
    }

    public static void detectLanguage(String text, StringCallback onSuccess, ExceptionCallback onFail) {
//        com.google.mlkit.nl.languageid.LanguageIdentification.getClient()
//            .identifyLanguage(text)
//            .addOnSuccessListener(onSuccess::run)
//            .addOnFailureListener(onFail::run);
    }
}
