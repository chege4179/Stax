package com.hover.stax.requests;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import com.amplitude.api.Amplitude;
import com.hover.stax.R;
import com.hover.stax.channels.Channel;
import com.hover.stax.contacts.PhoneHelper;
import com.hover.stax.contacts.StaxContact;
import com.hover.stax.utils.DateUtils;
import com.hover.stax.utils.Utils;
import com.hover.stax.utils.paymentLinkCryptography.Base64;
import com.hover.stax.utils.paymentLinkCryptography.Encryption;
import com.yariksoffice.lingver.Lingver;

import java.security.NoSuchAlgorithmException;
import java.util.List;

import timber.log.Timber;

@Entity(tableName = "requests")
public class Request {
  
    private final static String TAG = "Request";
    final public static String PAYMENT_LINK_SEPERATOR = "-";

    @PrimaryKey(autoGenerate = true)
    @NonNull
    public int id;

    @ColumnInfo(name = "description")
    public String description;

    @NonNull
    @ColumnInfo(name = "requestee_ids")
    public String requestee_ids;

    @ColumnInfo(name = "amount")
    public String amount;

    @ColumnInfo(name = "requester_institution_id")
    public int requester_institution_id;

    @ColumnInfo(name = "requester_number")
    public String requester_number;

    @ColumnInfo(name = "note")
    public String note;

    @ColumnInfo(name = "message")
    public String message;

    @ColumnInfo(name = "matched_transaction_uuid")
    public String matched_transaction_uuid;

    @NonNull
    @ColumnInfo(name = "date_sent", defaultValue = "CURRENT_TIMESTAMP")
    public Long date_sent;

    public Request() {
    }

    public Request(String amount, String note, String requester, int requester_institution_id) {
        this.amount = amount;
        this.note = note;
        this.requester_number = requester.replaceAll(" ", "");
        this.requester_institution_id = requester_institution_id;
        date_sent = DateUtils.now();
    }

    public Request(Request r, StaxContact requestee, Context c) {
        this.amount = r.amount;
        this.note = r.note;
        this.requestee_ids = requestee.id;
        this.requester_number = r.requester_number.replaceAll(" ", "");
        this.requester_institution_id = r.requester_institution_id;
        this.date_sent = r.date_sent;
        this.description = getDescription(requestee, c);
    }

    public Request(String paymentLink) {
        Timber.v("Creating request from link: " + paymentLink);
        String[] splitString = paymentLink.split(PAYMENT_LINK_SEPERATOR);
        amount = splitString[0].equals("0.00") ? "" : Utils.formatAmount(splitString[0]);
        requester_institution_id = Integer.parseInt(splitString[1]);
        requester_number = splitString[2];
    }

    public static boolean isShortLink(String link) {
        return link.length() <= 25;
    }

    public boolean hasRequesterInfo() {
        return requester_number != null && !requester_number.isEmpty();
    }

    public String getDescription(StaxContact contact, Context c) {
        return c.getString(R.string.descrip_request, contact.shortName());
    }

    String generateRecipientString(List<StaxContact> contacts) {
        StringBuilder phones = new StringBuilder();
        for (int r = 0; r < contacts.size(); r++) {
            if (phones.length() > 0) phones.append(",");
            phones.append(contacts.get(r).accountNumber);
        }
        return phones.toString();
    }

    String generateWhatsappRecipientString(List<StaxContact> contacts, Channel c) {
        StringBuilder phones = new StringBuilder();
        for (int r = 0; r < contacts.size(); r++) {
            if (phones.length() > 0) phones.append(",");
            phones.append(PhoneHelper.getInternationalNumberNoPlus(contacts.get(r).accountNumber, c != null ? c.countryAlpha2 : Lingver.getInstance().getLocale().getCountry()));
        }
        return phones.toString();
    }

    public String generateMessage(Context c) {
        String amountString = amount != null ? c.getString(R.string.sms_amount_detail, Utils.formatAmount(amount)) : "";
        String noteString = note != null ? c.getString(R.string.sms_note_detail, note) : "";
        String paymentLink = generateStaxLink(c);

        return c.getString(R.string.sms_request_template, amountString, noteString, paymentLink);
    }

    private String generateStaxLink(Context c) {
        String amountNoFormat = amount != null && !amount.isEmpty() ? amount : "0.00";
        String params = c.getString(R.string.payment_url_end, amountNoFormat, requester_institution_id, requester_number, DateUtils.now());
        Log.e(TAG, "encrypting from: " + params);
        try {
            Encryption encryption = Request.getEncryptionSettings().build();
            String encryptedString = encryption.encryptOrNull(params);
            Log.e(TAG, "link: " + c.getResources().getString(R.string.payment_root_url, encryptedString.replaceAll("[+]", "(")));
            //replacing + for () because of Uri escaping in line 164 for whatsapp. Chose () cos the letter is not generated by the encryption
            return c.getResources().getString(R.string.payment_root_url, encryptedString.replaceAll("[+]", "("));
        } catch (NoSuchAlgorithmException e) {
            Amplitude.getInstance().logEvent(c.getString(R.string.link_encryptfail));
            return null;
        }
    }

    public static Encryption.Builder getEncryptionSettings() {
        //PUTTING THIS HERE FOR NOW, BUT THIS SETTINGS OUGHT TO BE IN THE REPO SO SETTINGS COMES FROM ONLINE SERVER.
        return new Encryption.Builder()
                .setKeyLength(128)
                .setKeyAlgorithm("AES")
                .setCharsetName("UTF8")
                .setIterationCount(65536)
                .setKey("ves€Z€xs€aBKgh")
                .setDigestAlgorithm("SHA1")
                .setSalt("A secured salt")
                .setBase64Mode(Base64.DEFAULT)
                .setAlgorithm("AES/CBC/PKCS5Padding")
                .setSecureRandomAlgorithm("SHA1PRNG")
                .setSecretKeyType("PBKDF2WithHmacSHA1")
                .setIv(new byte[]{29, 88, -79, -101, -108, -38, -126, 90, 52, 101, -35, 114, 12, -48, -66, -30});
    }

    @NonNull
    @Override
    public String toString() {
        return description == null ? "Request from " + requester_number : description;
    }
}
