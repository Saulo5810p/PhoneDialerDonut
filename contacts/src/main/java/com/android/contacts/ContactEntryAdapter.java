/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.contacts;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Parcel;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Im;
import android.provider.ContactsContract.CommonDataKinds.Note;
import android.provider.ContactsContract.CommonDataKinds.Organization;
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import java.util.ArrayList;

public abstract class ContactEntryAdapter<E extends ContactEntryAdapter.Entry>
        extends BaseAdapter {

    // MIGRAÇÃO PROFUNDA: o provider antigo (People) guardava telefone/email/endereço/IM
    // como "ContactMethods" numa tabela única com coluna KIND. O ContactsContract
    // moderno separa cada tipo em sua própria tabela (Email, StructuredPostal, Im),
    // unidas pela tabela genérica Data via MIMETYPE. Os métodos queryContactMethods()
    // e queryPhones() abaixo reconstroem um cursor "no formato antigo" (via MatrixCursor)
    // pra minimizar mudanças no resto do código que consome essas projeções.

    public static final String[] CONTACT_PROJECTION = new String[] {
        Contacts._ID, // 0
        Contacts.DISPLAY_NAME, // 1
        Contacts.CONTACT_PRESENCE, // 2 (era PRESENCE_STATUS, índice 4 na versão antiga)
        Contacts.STARRED, // 3
        Contacts.CUSTOM_RINGTONE, // 4
        Contacts.SEND_TO_VOICEMAIL, // 5
        Contacts.PHONETIC_NAME, // 6
    };
    public static final int CONTACT_ID_COLUMN = 0;
    public static final int CONTACT_NAME_COLUMN = 1;
    public static final int CONTACT_SERVER_STATUS_COLUMN = 2;
    public static final int CONTACT_STARRED_COLUMN = 3;
    public static final int CONTACT_CUSTOM_RINGTONE_COLUMN = 4;
    public static final int CONTACT_SEND_TO_VOICEMAIL_COLUMN = 5;
    public static final int CONTACT_PHONETIC_NAME_COLUMN = 6;
    // NOTES e PRIMARY_PHONE_ID não existem mais como coluna direta do contato:
    // notas viraram uma linha própria na tabela Data (Note.CONTENT_ITEM_TYPE) e não
    // existe mais "telefone principal" denormalizado. Use getContactNote() abaixo
    // no lugar de CONTACT_NOTES_COLUMN, e HAS_PHONE_NUMBER (consulta separada) no
    // lugar de CONTACT_PREFERRED_PHONE_COLUMN.

    public static final String[] PHONES_PROJECTION = new String[] {
        android.provider.ContactsContract.CommonDataKinds.Phone._ID, // 0
        android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER, // 1
        android.provider.ContactsContract.CommonDataKinds.Phone.TYPE, // 2
        android.provider.ContactsContract.CommonDataKinds.Phone.LABEL, // 3
        android.provider.ContactsContract.CommonDataKinds.Phone.IS_PRIMARY, // 4
    };
    public static final int PHONES_ID_COLUMN = 0;
    public static final int PHONES_NUMBER_COLUMN = 1;
    public static final int PHONES_TYPE_COLUMN = 2;
    public static final int PHONES_LABEL_COLUMN = 3;
    public static final int PHONES_ISPRIMARY_COLUMN = 4;

    // "KIND" sintético local (não é mais um valor do provider — é só a forma da gente
    // saber, olhando a linha do MatrixCursor mesclado, se é email/endereço/IM).
    public static final int KIND_EMAIL = 1;
    public static final int KIND_POSTAL = 2;
    public static final int KIND_IM = 3;
    public static final int KIND_PHONE = 4;
    public static final int KIND_ORGANIZATION = 5;

    public static final String[] METHODS_PROJECTION = new String[] {
        "_id", "kind", "data", "type", "label", "isprimary", "aux_data",
    };
    public static final String[] METHODS_WITH_PRESENCE_PROJECTION = new String[] {
        "_id", "kind", "data", "type", "label", "isprimary", "aux_data", "presence",
    };
    public static final int METHODS_ID_COLUMN = 0;
    public static final int METHODS_KIND_COLUMN = 1;
    public static final int METHODS_DATA_COLUMN = 2;
    public static final int METHODS_TYPE_COLUMN = 3;
    public static final int METHODS_LABEL_COLUMN = 4;
    public static final int METHODS_ISPRIMARY_COLUMN = 5;
    public static final int METHODS_AUX_DATA_COLUMN = 6;
    public static final int METHODS_STATUS_COLUMN = 7;

    public static final String[] ORGANIZATIONS_PROJECTION = new String[] {
        Organization._ID, // 0
        Organization.TYPE, // 1
        Organization.LABEL, // 2
        Organization.COMPANY, // 3
        Organization.TITLE, // 4
        Organization.IS_PRIMARY, // 5
    };
    public static final int ORGANIZATIONS_ID_COLUMN = 0;
    public static final int ORGANIZATIONS_TYPE_COLUMN = 1;
    public static final int ORGANIZATIONS_LABEL_COLUMN = 2;
    public static final int ORGANIZATIONS_COMPANY_COLUMN = 3;
    public static final int ORGANIZATIONS_TITLE_COLUMN = 4;
    public static final int ORGANIZATIONS_ISPRIMARY_COLUMN = 5;

    /**
     * Busca a nota do contato (era CONTACT_NOTES_COLUMN direto no cursor; agora é uma
     * linha própria na tabela Data). Devolve null se não houver nota.
     */
    public static String getContactNote(Context context, long contactId) {
        ContentResolver resolver = context.getContentResolver();
        Cursor c = resolver.query(Data.CONTENT_URI,
                new String[] { Note.NOTE },
                Data.CONTACT_ID + "=? AND " + Data.MIMETYPE + "=?",
                new String[] { String.valueOf(contactId), Note.CONTENT_ITEM_TYPE }, null);
        try {
            if (c != null && c.moveToFirst()) {
                return c.getString(0);
            }
        } finally {
            if (c != null) c.close();
        }
        return null;
    }

    /**
     * Reconstrói um cursor "no formato ContactMethods antigo" mesclando Email,
     * StructuredPostal e Im do contato numa única tabela virtual (MatrixCursor),
     * na mesma forma de METHODS_PROJECTION / METHODS_WITH_PRESENCE_PROJECTION.
     */
    public static Cursor queryContactMethods(Context context, long contactId,
            boolean withPresence) {
        ContentResolver resolver = context.getContentResolver();
        String[] cols = withPresence ? METHODS_WITH_PRESENCE_PROJECTION : METHODS_PROJECTION;
        MatrixCursor result = new MatrixCursor(cols);
        String selection = Data.CONTACT_ID + "=?";
        String[] args = new String[] { String.valueOf(contactId) };

        // Email
        Cursor c = resolver.query(Email.CONTENT_URI,
                withPresence
                    ? new String[] { Email._ID, Email.ADDRESS, Email.TYPE, Email.LABEL,
                            Email.IS_PRIMARY, Email.CONTACT_PRESENCE }
                    : new String[] { Email._ID, Email.ADDRESS, Email.TYPE, Email.LABEL,
                            Email.IS_PRIMARY },
                selection, args, null);
        if (c != null) {
            try {
                while (c.moveToNext()) {
                    MatrixCursor.RowBuilder row = result.newRow();
                    row.add(c.getLong(0)).add(KIND_EMAIL).add(c.getString(1))
                            .add(c.getInt(2)).add(c.getString(3)).add(c.getInt(4))
                            .add((String) null);
                    if (withPresence) row.add(c.isNull(5) ? null : c.getInt(5));
                }
            } finally { c.close(); }
        }

        // Endereço postal
        c = resolver.query(StructuredPostal.CONTENT_URI,
                new String[] { StructuredPostal._ID, StructuredPostal.FORMATTED_ADDRESS,
                        StructuredPostal.TYPE, StructuredPostal.LABEL,
                        StructuredPostal.IS_PRIMARY },
                selection, args, null);
        if (c != null) {
            try {
                while (c.moveToNext()) {
                    MatrixCursor.RowBuilder row = result.newRow();
                    row.add(c.getLong(0)).add(KIND_POSTAL).add(c.getString(1))
                            .add(c.getInt(2)).add(c.getString(3)).add(c.getInt(4))
                            .add((String) null);
                    if (withPresence) row.add((Integer) null);
                }
            } finally { c.close(); }
        }

        // IM — aux_data guarda o protocolo (int) como string, no lugar do encode antigo
        // Im nao tem CONTENT_URI proprio (e um "data kind" dentro de Data) - por isso
        // aqui, diferente de Email/StructuredPostal acima, precisa filtrar por MIMETYPE.
        c = resolver.query(Data.CONTENT_URI,
                withPresence
                    ? new String[] { Im._ID, Im.DATA, Im.TYPE, Im.LABEL, Im.IS_PRIMARY,
                            Im.PROTOCOL, Im.CONTACT_PRESENCE }
                    : new String[] { Im._ID, Im.DATA, Im.TYPE, Im.LABEL, Im.IS_PRIMARY,
                            Im.PROTOCOL },
                selection + " AND " + Data.MIMETYPE + "=?",
                new String[] { String.valueOf(contactId), Im.CONTENT_ITEM_TYPE }, null);
        if (c != null) {
            try {
                while (c.moveToNext()) {
                    MatrixCursor.RowBuilder row = result.newRow();
                    row.add(c.getLong(0)).add(KIND_IM).add(c.getString(1))
                            .add(c.getInt(2)).add(c.getString(3)).add(c.getInt(4))
                            .add(String.valueOf(c.getInt(5)));
                    if (withPresence) row.add(c.isNull(6) ? null : c.getInt(6));
                }
            } finally { c.close(); }
        }

        return result;
    }
    protected ArrayList<ArrayList<E>> mSections;
    protected LayoutInflater mInflater;
    protected Context mContext;
    protected boolean mSeparators;

    /**
     * Base class for adapter entries.
     */
    public static class Entry {
        /** Details from the person table */
        public static final int KIND_CONTACT = -1;
        /** Synthesized phone entry that will send an SMS instead of call the number */
        public static final int KIND_SMS = -2;
        /** A section separator */
        public static final int KIND_SEPARATOR = -3; 

        public String label;
        public String data;
        public Uri uri;
        public long id = 0;
        public int maxLines = 1;
        public int kind;
        
        /**
         * Helper for making subclasses parcelable.
         */
        protected void writeToParcel(Parcel p) {
            p.writeString(label);
            p.writeString(data);
            p.writeParcelable(uri, 0);
            p.writeLong(id);
            p.writeInt(maxLines);
            p.writeInt(kind);
        }
        
        /**
         * Helper for making subclasses parcelable.
         */
        protected void readFromParcel(Parcel p) {
            label = p.readString();
            data = p.readString();
            uri = p.readParcelable(null);
            id = p.readLong();
            maxLines = p.readInt();
            kind = p.readInt();
        }
    }

    ContactEntryAdapter(Context context, ArrayList<ArrayList<E>> sections, boolean separators) {
        mContext = context;
        mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mSections = sections;
        mSeparators = separators;
    }

    /**
     * Resets the section data.
     * 
     * @param sections the section data
     */
    public final void setSections(ArrayList<ArrayList<E>> sections, boolean separators) {
        mSections = sections;
        mSeparators = separators;
        notifyDataSetChanged();
    }

    /**
     * Resets the section data and returns the position of the given entry.
     * 
     * @param sections the section data
     * @param entry the entry to return the position for
     * @return the position of entry, or -1 if it isn't found
     */
    public final int setSections(ArrayList<ArrayList<E>> sections, E entry) {
        mSections = sections;
        notifyDataSetChanged();

        int numSections = mSections.size();
        int position = 0;
        for (int i = 0; i < numSections; i++) {
            ArrayList<E> section = mSections.get(i);
            int sectionSize = section.size();
            for (int j = 0; j < sectionSize; j++) {
                E e = section.get(j);
                if (e.equals(entry)) {
                    position += j;
                    return position;
                }
            }
            position += sectionSize;
        }
        return -1;
    }

    /**
     * @see android.widget.ListAdapter#getCount()
     */
    public final int getCount() {
        return countEntries(mSections, mSeparators);
    }

    /**
     * @see android.widget.ListAdapter#hasSeparators()
     */
    @Override
    public final boolean areAllItemsEnabled() {
        return mSeparators == false;
    }

    /**
     * @see android.widget.ListAdapter#isSeparator(int)
     */
    @Override
    public final boolean isEnabled(int position) {
        if (!mSeparators) {
            return true;
        }

        int numSections = mSections.size();
        for (int i = 0; i < numSections; i++) {
            ArrayList<E> section = mSections.get(i);
            int sectionSize = section.size();
            if (sectionSize == 1) {
                // The section only contains a separator and nothing else, skip it
                continue;
            }
            if (position == 0) {
                // The first item in a section is always the separator
                return false;
            }
            position -= sectionSize;
        }
        return true;
    }

    /**
     * @see android.widget.ListAdapter#getItem(int)
     */
    public final Object getItem(int position) {
        return getEntry(mSections, position, mSeparators);
    }

    /**
     * Get the entry for the given position.
     * 
     * @param sections the list of sections
     * @param position the position for the desired entry
     * @return the ContactEntry for the given position
     */
    public final static <T extends Entry> T getEntry(ArrayList<ArrayList<T>> sections,
            int position, boolean separators) {
        int numSections = sections.size();
        for (int i = 0; i < numSections; i++) {
            ArrayList<T> section = sections.get(i);
            int sectionSize = section.size();
            if (separators && sectionSize == 1) {
                // The section only contains a separator and nothing else, skip it
                continue;
            }
            if (position < section.size()) {
                return section.get(position);
            }
            position -= section.size();
        }
        return null;
    }

    /**
     * Get the count of entries in all sections
     * 
     * @param sections the list of sections
     * @return the count of entries in all sections
     */
    public static <T extends Entry> int countEntries(ArrayList<ArrayList<T>> sections,
            boolean separators) {
        int count = 0;
        int numSections = sections.size();
        for (int i = 0; i < numSections; i++) {
            ArrayList<T> section = sections.get(i);
            int sectionSize = section.size();
            if (separators && sectionSize == 1) {
                // The section only contains a separator and nothing else, skip it
                continue;
            }
            count += sections.get(i).size();
        }
        return count;
    }

    /**
     * @see android.widget.ListAdapter#getItemId(int)
     */
    public final long getItemId(int position) {
        Entry entry = getEntry(mSections, position, mSeparators);
        if (entry != null) {
            return entry.id;
        } else {
            return -1;
        }
    }

    /**
     * @see android.widget.ListAdapter#getView(int, View, ViewGroup)
     */
    public View getView(int position, View convertView, ViewGroup parent) {
        View v;
        if (convertView == null) {
            v = newView(position, parent);
        } else {
            v = convertView;
        }
        bindView(v, getEntry(mSections, position, mSeparators));
        return v;
    }

    /**
     * Create a new view for an entry.
     * 
     * @parent the parent ViewGroup
     * @return the newly created view
     */
    protected abstract View newView(int position, ViewGroup parent);

    /**
     * Binds the data from an entry to a view.
     * 
     * @param view the view to display the entry in
     * @param entry the data to bind
     */
    protected abstract void bindView(View view, E entry);
}
