package com.android.contacts.compat;

// Substituto de com.android.internal.telephony.ITelephony (AIDL interno de sistema,
// inacessível a apps normais). Cada método aqui reimplementa o equivalente funcional
// usando só API pública (TelephonyManager / TelecomManager), disponível a partir da
// API 23 conforme o minSdk do projeto.
//
// showCallScreen() é o único caso sem equivalente público direto: na versão original
// ele pedia pro processo do sistema trazer a InCallScreen dele mesmo pra frente. Aqui,
// como o nosso :phone é quem implementa InCallService, ele já é o dono da tela de
// chamada — então esse método simplesmente inicia a nossa própria InCallActivity,
// que só tem efeito real se já existir uma chamada em andamento gerenciada por nós
// (mesma limitação que o Saulo já esperava: "só funciona durante uma chamada").

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.telecom.TelecomManager;
import android.telephony.TelephonyManager;

public class TelephonyCompat {

    // pacote/classe da nossa InCallActivity no módulo :phone — ajustado quando essa
    // classe for criada na Fase do Phone (mantido como String pra não criar
    // dependência de compilação entre os dois módulos, que são apps separados)
    private static final String INCALL_ACTIVITY_ACTION =
            "com.xaulinxs.donut.telefoneantigo.SHOW_INCALL";

    public static boolean isIdle(Context context) {
        TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        if (tm == null) return true;
        return tm.getCallState() == TelephonyManager.CALL_STATE_IDLE;
    }

    public static void cancelMissedCallsNotification(Context context) {
        TelecomManager telecom = (TelecomManager) context.getSystemService(Context.TELECOM_SERVICE);
        if (telecom == null) return;
        try {
            telecom.cancelMissedCallsNotification();
        } catch (SecurityException e) {
            // precisa ser o app de telefone padrão ou ter READ_PHONE_STATE; se não
            // tiver, simplesmente não limpa a notificação — não é crítico
        }
    }

    public static boolean showCallScreen(Context context, boolean showDialpad) {
        Intent intent = new Intent(INCALL_ACTIVITY_ACTION);
        intent.setPackage(context.getPackageName().equals("com.android.contacts")
                ? "com.xaulinxs.donut.telefoneantigo" : context.getPackageName());
        intent.putExtra("showDialpad", showDialpad);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(intent);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // Substitui ITelephony.handlePinMmi(input). Só existe API pública equivalente
    // (TelecomManager#handleMmi) a partir da API 26; abaixo disso não há alternativa
    // pública — devolve false e quem chamou trata a sequência como um número comum.
    public static boolean handlePinMmi(Context context, String input) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            TelecomManager telecom = (TelecomManager) context.getSystemService(Context.TELECOM_SERVICE);
            if (telecom != null) {
                try {
                    return telecom.handleMmi(input);
                } catch (SecurityException e) {
                    return false;
                }
            }
        }
        return false;
    }
}
