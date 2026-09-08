package com.android.contacts.compat;

// Substituto local de com.android.internal.telephony.CallerInfo (classe de sistema,
// inacessível a apps normais). Reimplementação enxuta cobrindo só os campos que o
// Contacts original usava: nome, número, tipo/label, foto e flags de contato.
//
// getInstance() faz a consulta síncrona ao ContactsContract moderno (PhoneLookup),
// equivalente ao que a versão original fazia contra o provider antigo android.provider.Contacts.

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.PhoneLookup;

public class CallerInfo {
    // Valores especiais que o CallLog/RIL usam no lugar de um número real (chamada
    // desconhecida, bloqueada ou de orelhão). Mesmos valores da classe interna original
    // — são convenções antigas do provider de telefonia, não específicas da API interna.
    public static final String UNKNOWN_NUMBER = "-1";
    public static final String PRIVATE_NUMBER = "-2";
    public static final String PAYPHONE_NUMBER = "-3";

    public String name;
    public String phoneNumber;
    public String phoneLabel;
    public int numberType;
    public long contactId = -1;
    public boolean contactExists;
    public boolean isEmergencyNumber;
    public boolean isVoiceMailNumber;
    public Uri contactRefUri;
    public Uri photoUri;

    public static CallerInfo getInstance(Context context, String number) {
        CallerInfo info = new CallerInfo();
        info.phoneNumber = number;

        if (number == null || number.isEmpty()) {
            return info;
        }

        Uri uri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number));
        String[] projection = {
                PhoneLookup._ID,
                PhoneLookup.DISPLAY_NAME,
                PhoneLookup.PHOTO_URI,
                PhoneLookup.TYPE,
                PhoneLookup.LABEL
        };

        try (Cursor cursor = context.getContentResolver().query(uri, projection, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                info.contactExists = true;
                info.contactId = cursor.getLong(cursor.getColumnIndexOrThrow(PhoneLookup._ID));
                info.name = cursor.getString(cursor.getColumnIndexOrThrow(PhoneLookup.DISPLAY_NAME));
                info.numberType = cursor.getInt(cursor.getColumnIndexOrThrow(PhoneLookup.TYPE));
                info.phoneLabel = Phone.getTypeLabel(context.getResources(), info.numberType,
                        cursor.getString(cursor.getColumnIndexOrThrow(PhoneLookup.LABEL))).toString();
                String photo = cursor.getString(cursor.getColumnIndexOrThrow(PhoneLookup.PHOTO_URI));
                info.photoUri = (photo != null) ? Uri.parse(photo) : null;
                info.contactRefUri = ContactsContract.Contacts.getLookupUri(info.contactId,
                        String.valueOf(info.contactId));
            }
        } catch (SecurityException e) {
            // sem permissão READ_CONTACTS concedida ainda — devolve info vazia,
            // quem chamou decide se pede a permissão
        }

        return info;
    }
}
