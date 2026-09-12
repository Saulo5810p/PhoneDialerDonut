/*
 * Helper novo (não existia no Android 1.6 original).
 *
 * Intent.ACTION_CALL_EMERGENCY e Intent.ACTION_CALL_PRIVILEGED são
 * constantes @hide/internas no SDK público desde muitas versões atrás --
 * ainda existem em tempo de execução dentro do próprio framework (o
 * discador de emergência do sistema as usa por baixo), mas o compilador
 * não enxerga o campo porque ele não faz parte do android.jar público.
 *
 * Os valores de string em si são estáveis (fazem parte do contrato de
 * broadcast do sistema, não mudam entre versões), então replicá-los aqui
 * é seguro -- é o mesmo padrão já usado em compat/CallerInfo.java e
 * compat/TelephonyCompat.java pro resto do projeto.
 */
package com.android.phone.compat;

public final class TelephonyIntentsCompat {
    private TelephonyIntentsCompat() {}

    /** Equivalente a Intent.ACTION_CALL_EMERGENCY (@hide). */
    public static final String ACTION_CALL_EMERGENCY =
            "android.intent.action.CALL_EMERGENCY";

    /** Equivalente a Intent.ACTION_CALL_PRIVILEGED (@hide). */
    public static final String ACTION_CALL_PRIVILEGED =
            "android.intent.action.CALL_PRIVILEGED";
}
