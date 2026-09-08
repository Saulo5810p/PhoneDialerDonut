package com.android.phone.compat;

// Mesmo papel do com.android.contacts.compat.CallerInfo: substituto local de
// com.android.internal.telephony.CallerInfo (classe de sistema, inacessível a
// apps normais). Reimplementação enxuta usando só ContactsContract.PhoneLookup
// (API pública). Duplicada aqui em vez de compartilhada porque Phone e Contacts
// são dois módulos/APKs Gradle separados, sem dependência de compilação entre si.

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.PhoneLookup;

public class CallerInfo {
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

    public static CallerInfo getCallerInfo(Context context, String number) {
        CallerInfo info = new CallerInfo();
        info.phoneNumber = number;

        if (number == null || number.isEmpty()) {
            return info;
        }

        if (android.telephony.PhoneNumberUtils.isEmergencyNumber(number)) {
            info.isEmergencyNumber = true;
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
            // sem permissão READ_CONTACTS ainda — devolve info vazia
        }

        return info;
    }
}
