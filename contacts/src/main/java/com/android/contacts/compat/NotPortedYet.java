/*
 * Helper novo (não existia no Android 1.6 original) — cópia do
 * com.android.phone.compat.NotPortedYet, adaptada pro módulo contacts.
 *
 * Usado nas telas que dependiam de API interna sem NENHUM equivalente
 * público hoje (aqui: import/export de contatos via VCard, que dependia
 * inteiramente de android.syncml.pim.*, nunca pública em nenhuma versão
 * do Android).
 *
 * Decisão do Saulo: manter o ponto de entrada (o item de menu) na tela,
 * mas a ação vira este aviso em vez de tentar rodar um parser de VCard
 * que não existe mais pra apps normais.
 */
package com.android.contacts.compat;

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
