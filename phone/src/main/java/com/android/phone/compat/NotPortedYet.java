/*
 * Helper novo (não existia no Android 1.6 original).
 *
 * Usado nas telas que dependiam de com.android.internal.telephony (gestão de
 * PIN/PUK/FDN do SIM, seleção manual de operadora, roaming/subscription CDMA,
 * cell broadcast) para as quais o Android moderno NÃO oferece nenhum
 * equivalente público a um app comum, com ou sem root -- essas operações são
 * signature-only, reservadas ao processo de sistema (Keyguard/Settings).
 *
 * Decisão do Saulo: manter o layout original de 2009 na tela (pra não perder
 * nada do visual que a Rota A quer preservar), mas fazer os botões de ação
 * mostrarem esta mensagem em vez de tentar chamar uma API que não existe mais
 * pra apps normais. Hoje é um Toast simples; quando o tema clássico for
 * integrado, isso pode virar uma caixinha no estilo do diálogo antigo.
 */
package com.android.phone.compat;

import android.content.Context;
import android.widget.Toast;

public final class NotPortedYet {
    private NotPortedYet() {}

    public static void show(Context context) {
        Toast.makeText(context,
                "Eu até consigo portar isso aqui, só não faço milagre",
                Toast.LENGTH_LONG).show();
    }
}
