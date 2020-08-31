package com.hover.stax.pins;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.hover.sdk.api.HoverParameters;
import com.hover.stax.MainActivity;
import com.hover.stax.R;
import com.hover.stax.channels.Channel;
import com.hover.stax.database.KeyStoreExecutor;
import com.hover.stax.home.BalanceModel;
import com.hover.stax.utils.UIHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PinsActivity extends AppCompatActivity {

	private PinsViewModel channelViewModel;
	private PinEntryAdapter pinEntryAdapter;
	private static final int RUN_ALL_RESULT = 301;
	private int actionRunCounter = 0;
	private List<BalanceModel> balanceModelList;


	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.services_pin_layout);
		balanceModelList = new ArrayList<>();
		channelViewModel = new ViewModelProvider(this).get(PinsViewModel.class);


		RecyclerView pinRecyclerView = findViewById(R.id.pin_recyclerView);
		channelViewModel.getSelectedChannels().observe(this, channels -> {

			for(Channel c: channels) {
				Log.d("SWEET", c.pin == null ? "null" : c.pin);
			}
			pinRecyclerView.setLayoutManager(UIHelper.setMainLinearManagers(this));
			pinRecyclerView.setHasFixedSize(true);
			pinEntryAdapter = new PinEntryAdapter(channels);

			pinRecyclerView.setAdapter(pinEntryAdapter);
		});

		channelViewModel.getBalances().observe(this, balanceModels -> {
		if(balanceModels.size()> 0) {
			Log.d("SWEET", "running of action can be initiated");
			balanceModelList = balanceModels;
			runAction(true);
		}
		else {
			Log.d("SWEET", "cant run yet");
		}
		});

		findViewById(R.id.choose_serves_cancel).setOnClickListener(view -> finish());

		findViewById(R.id.continuePinButton).setOnClickListener(view -> {
			channelViewModel.savePins(pinEntryAdapter.retrieveChannels(), this);
		});
	}

private void runAction(boolean firstTime) {
		BalanceModel balanceModel = balanceModelList.get(actionRunCounter);
	HoverParameters.Builder builder = new HoverParameters.Builder(this);

	builder.request(balanceModel.getActionId());
	builder.setEnvironment(HoverParameters.DEBUG_ENV);
	builder.style(R.style.myHoverTheme);
//        builder.initialProcessingMessage(getResources().getString(R.string.transaction_coming_up));
	builder.finalMsgDisplayTime(2000);
	Log.d("sweet", "pin is: "+balanceModel.getEncryptedPin());
	builder.extra("pin",balanceModel.getEncryptedPin() );

	if(firstTime) actionRunCounter = actionRunCounter + 1;
	Intent i = builder.buildIntent();
	startActivityForResult(i, RUN_ALL_RESULT);

}

@Override
protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
	super.onActivityResult(requestCode, resultCode, data);
	if (requestCode == RUN_ALL_RESULT) {
		if(actionRunCounter  < balanceModelList.size()) {
			new Handler().postDelayed(() -> {
				runAction(false);
				actionRunCounter = actionRunCounter + 1;
			}, 2000);


		}
		else if(actionRunCounter == balanceModelList.size()) {
			//Important to set runCounter back to zero when completed.
			MainActivity.GO_TO_SPLASH_SCREEN = false;
			startActivity(new Intent(this, MainActivity.class));
		}
	}

}
}
