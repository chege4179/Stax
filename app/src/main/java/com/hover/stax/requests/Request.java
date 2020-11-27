package com.hover.stax.requests;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import com.amplitude.api.Amplitude;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;
import com.hover.stax.R;
import com.hover.stax.database.Constants;
import com.hover.stax.contacts.StaxContact;
import com.hover.stax.utils.DateUtils;
import com.hover.stax.utils.PermissionUtils;
import com.hover.stax.utils.UIHelper;
import com.hover.stax.utils.paymentLinkCryptography.Base64;
import com.hover.stax.utils.paymentLinkCryptography.Encryption;

import java.security.NoSuchAlgorithmException;
import java.util.List;

import io.sentry.Sentry;

@Entity(tableName = "requests")
public class Request {

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

	@ColumnInfo(name  = "requestee_number")
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

	public Request() { }

	public Request(StaxContact requestee, String a, String n, String requester_number, int institution_id, Context context) {
		requestee_ids = requestee.id;
		amount = a;
		note = n;
		requester_institution_id = institution_id;
		this.requester_number = requester_number;
		date_sent = DateUtils.now();
		description = getDescription(requestee, context);
	}

	public String getDescription(StaxContact contact, Context c) {
		return c.getString(R.string.descrip_request, contact.shortName());
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
					   .setIv(new byte[] { 29, 88, -79, -101, -108, -38, -126, 90, 52, 101, -35, 114, 12, -48, -66, -30 });
	}

	 static String generateStaxLink(String amount, int channel_id, String accountNumber, Context c) {
		if (channel_id == 0 || accountNumber.isEmpty()) {
			Amplitude.getInstance().logEvent(c.getString(R.string.stax_link_encryption_failure_1));
			return null;
		}
		String separator = Constants.PAYMENT_LINK_SEPERATOR;
		String fullString = amount+separator+channel_id +separator+accountNumber+separator+DateUtils.now();

		try {
			Encryption encryption =  Request.getEncryptionSettings().build();
			String encryptedString = encryption.encryptOrNull(fullString);
			return c.getResources().getString(R.string.payment_root_url)+encryptedString;

		} catch (NoSuchAlgorithmException e) {
			Amplitude.getInstance().logEvent(c.getString(R.string.stax_link_encryption_failure_2));
			return null;
		}
	}

	static void sendUsingSms(NewRequestViewModel requestViewModel, SmsSentObserver.SmsSentListener smsSentListener, Context context, Activity activity) {
		Amplitude.getInstance().logEvent(context.getString(R.string.clicked_send_sms_request));
		if (!PermissionUtils.hasSmsPermission(context))
			activity.requestPermissions(new String[]{Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS}, Constants.SMS);
		else{
			requestViewModel.setStarted();
			new SmsSentObserver(smsSentListener, requestViewModel.getRecipients().getValue(), new Handler(), context).start();

			Intent sendIntent = new Intent();
			sendIntent.setAction(Intent.ACTION_VIEW);
			sendIntent.setData(Uri.parse("smsto:" + requestViewModel.generateRecipientString()));
			sendIntent.putExtra(Intent.EXTRA_TEXT, requestViewModel.generateSMS());
			sendIntent.putExtra("sms_body", requestViewModel.generateSMS());
			activity.startActivityForResult(Intent.createChooser(sendIntent, "Request"), Constants.SMS);
		}
	}

	static void sendUsingSms(String recipients, String smsContent, Context context, Activity activity) {
		Amplitude.getInstance().logEvent(context.getString(R.string.clicked_send_sms_request));
		if (!PermissionUtils.hasSmsPermission(context))
			activity.requestPermissions(new String[]{Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS}, Constants.SMS);
		else{
			Intent sendIntent = new Intent();
			sendIntent.setAction(Intent.ACTION_VIEW);
			sendIntent.setData(Uri.parse("smsto:" + recipients));
			sendIntent.putExtra(Intent.EXTRA_TEXT, smsContent);
			sendIntent.putExtra("sms_body", smsContent);
			activity.startActivityForResult(Intent.createChooser(sendIntent, "Request"), Constants.SMS);
		}
	}
	static void  sendUsingWhatsapp(String recipient, String countryAlpha, String smsContent, Context context, Activity activity) {
		Amplitude.getInstance().logEvent(context.getString(R.string.clicked_send_whatsapp_request));
		Intent sendIntent = new Intent();
		sendIntent.setAction(Intent.ACTION_VIEW);

		PhoneNumberUtil phoneUtil = PhoneNumberUtil.getInstance();
		try {
			Phonenumber.PhoneNumber formattedPhone = phoneUtil.parse(recipient, countryAlpha);
			String whatsapp ="https://api.whatsapp.com/send?phone="+formattedPhone +"&text=" + smsContent;
			sendIntent.setData(Uri.parse(whatsapp));
			activity.startActivityForResult(sendIntent, Constants.SMS);
		} catch (NumberParseException e) {
			Sentry.capture(e.getMessage());
		}
	}
}
