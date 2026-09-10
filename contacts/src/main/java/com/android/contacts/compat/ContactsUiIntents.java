/*
 * Substituto local da antiga android.provider.Contacts.Intents.UI (e do
 * android.provider.ContactsContract.Intents.UI equivalente do Donut), classe
 * removida do SDK moderno. Os valores abaixo são os mesmos literais que a
 * classe original tinha (conferidos na documentação arquivada da API 1-4) -
 * o app inteiro (Activities + AndroidManifest.xml) já usa essas strings, só
 * precisava de um lugar para elas existirem como constantes Java de novo.
 */
package com.android.contacts.compat;

public final class ContactsUiIntents {
    private ContactsUiIntents() {}

    public static final String LIST_DEFAULT =
            "com.android.contacts.action.LIST_DEFAULT";
    public static final String LIST_GROUP_ACTION =
            "com.android.contacts.action.LIST_GROUP";
    public static final String LIST_ALL_CONTACTS_ACTION =
            "com.android.contacts.action.LIST_ALL_CONTACTS";
    public static final String LIST_CONTACTS_WITH_PHONES_ACTION =
            "com.android.contacts.action.LIST_CONTACTS_WITH_PHONES";
    public static final String LIST_STARRED_ACTION =
            "com.android.contacts.action.LIST_STARRED";
    public static final String LIST_FREQUENT_ACTION =
            "com.android.contacts.action.LIST_FREQUENT";
    public static final String LIST_STREQUENT_ACTION =
            "com.android.contacts.action.LIST_STREQUENT";
    public static final String FILTER_CONTACTS_ACTION =
            "com.android.contacts.action.FILTER_CONTACTS";
    public static final String FILTER_TEXT_EXTRA_KEY =
            "com.android.contacts.extra.FILTER_TEXT";
    public static final String GROUP_NAME_EXTRA_KEY =
            "com.android.contacts.extra.GROUP";
    public static final String TITLE_EXTRA_KEY =
            "com.android.contacts.extra.TITLE_EXTRA";
}
