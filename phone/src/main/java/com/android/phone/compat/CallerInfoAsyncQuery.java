package com.android.phone.compat;

// Substituto de com.android.internal.telephony.CallerInfoAsyncQuery (classe de
// sistema). Faz a mesma coisa que a original fazia: consulta CallerInfo fora da
// UI thread e entrega o resultado de volta via callback, pra não travar a tela
// de chamada numa consulta de ContentResolver.
//
// Convenção de assinatura do listener: onQueryComplete(CallerInfo, Object cookie)
// — 2 argumentos, sem "token" (o token int da API antiga não tinha uso real além
// de rotear múltiplas queries simultâneas; como só fazemos uma consulta por vez
// aqui, foi simplificado).

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

public class CallerInfoAsyncQuery {

    public interface OnQueryCompleteListener {
        void onQueryComplete(CallerInfo info, Object cookie);
    }

    private static final Handler sMainHandler = new Handler(Looper.getMainLooper());

    /**
     * Dispara a consulta em background e entrega o resultado na UI thread via
     * listener.onQueryComplete(info, cookie).
     */
    public static void startQuery(final Context context, final String number,
            final OnQueryCompleteListener listener, final Object cookie) {
        new Thread("CallerInfoAsyncQuery") {
            @Override
            public void run() {
                final CallerInfo info = CallerInfo.getCallerInfo(context, number);
                sMainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        listener.onQueryComplete(info, cookie);
                    }
                });
            }
        }.start();
    }
}
