package com.hover.stax.paybill

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PorterDuff
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import com.hover.sdk.actions.HoverAction
import com.hover.stax.R
import com.hover.stax.channels.Channel
import com.hover.stax.channels.ChannelsViewModel
import com.hover.stax.databinding.FragmentPaybillBinding
import com.hover.stax.utils.Constants
import com.hover.stax.views.AbstractStatefulInput
import org.koin.androidx.viewmodel.ext.android.sharedViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel
import timber.log.Timber

class PaybillFragment : Fragment(), PaybillIconsAdapter.IconListener {

    private var _binding: FragmentPaybillBinding? = null
    private val binding get() = _binding!!

    private val channelsViewModel: ChannelsViewModel by viewModel()
    private val paybillViewModel: PaybillViewModel by sharedViewModel()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPaybillBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        channelsViewModel.setType(HoverAction.C2B)

        arguments?.getBoolean(UPDATE_BUSINESS_NO, false)?.let {
            binding.billDetailsLayout.businessNoInput.setText(paybillViewModel.businessNumber.value)
        }

        initListeners()
        startObservers()
        setWatchers()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initListeners() {
        with(binding.saveBillLayout) {
            saveBill.setOnCheckedChangeListener { _, isChecked ->
                binding.saveBillLayout.saveBillCard.visibility = if (isChecked) View.VISIBLE else View.GONE
            }

            billIconLayout.iconLayout.setOnClickListener { showIconsChooser() }
        }

        binding.billDetailsLayout.businessNoInput.editText.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                channelsViewModel.activeAccount.value?.id?.let {
                    findNavController().navigate(R.id.action_paybillFragment_to_paybillListFragment, bundleOf(Constants.ACCOUNT_ID to it))
                } ?: Timber.e("Active account not set")
                true
            } else false
        }

        binding.continueBtn.setOnClickListener {
            if (validates()) {
                if (paybillViewModel.isEditing.value == true) {
                    if (binding.saveBillLayout.saveBill.isChecked) {
                        paybillViewModel.savePaybill(channelsViewModel.activeAccount.value, binding.saveBillLayout.amountCheckBox.isChecked)
                    }

                    paybillViewModel.setEditing(false)
                    Timber.e("Here")
                } else {
                    //TODO run actions here
                    Timber.e("Is not editing")
                }
            } else {
                Timber.e("Not validated")
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun startObservers() {
        with(paybillViewModel) {
            selectedPaybill.observe(viewLifecycleOwner) {
                with(binding.billDetailsLayout) {
                    businessNoInput.setText(it.name)
                    accountNoInput.setText(it.accountNo)
                }
            }

            isEditing.observe(viewLifecycleOwner) {
                if (it == false)
                    showSummary()
                else
                    showContent()
            }

            iconDrawable.observe(viewLifecycleOwner) {
                if (it != 0) {
                    binding.saveBillLayout.billIconLayout.billIcon.setImageDrawable(ContextCompat.getDrawable(requireContext(), it))
                }
            }
        }

        with(channelsViewModel) {
            binding.billDetailsLayout.accountDropdown.apply {
                setListener(this@with)
                setObservers(this@with, viewLifecycleOwner)
            }

            setupActionDropdownObservers(this, viewLifecycleOwner)

            accounts.observe(viewLifecycleOwner) {
                if (it.isEmpty())
                    binding.billDetailsLayout.accountDropdown.autoCompleteTextView.setOnTouchListener { _, event ->
                        if (event.action == MotionEvent.ACTION_DOWN)
                            findNavController().navigate(R.id.action_paybillFragment_to_accountsFragment)
                        true
                    }
            }
        }
    }

    private fun setupActionDropdownObservers(viewModel: ChannelsViewModel, lifecycleOwner: LifecycleOwner) {

        val activeChannelObserver = object : Observer<Channel> {
            override fun onChanged(t: Channel?) {
                Timber.i("Got new active channel: $t ${t?.countryAlpha2}")
            }
        }

        val actionsObserver = object : Observer<List<HoverAction>> {
            override fun onChanged(t: List<HoverAction>?) {
                Timber.i("Got new actions: %s", t?.size)
            }
        }

        viewModel.activeChannel.observe(lifecycleOwner, activeChannelObserver)
        viewModel.channelActions.observe(lifecycleOwner, actionsObserver)
    }

    private fun showContent() {
        with(binding) {
            paybillSummary.paybillSummaryCard.visibility = View.GONE
            toggleMainContent(true)

            continueBtn.text = getString(R.string.btn_continue)
        }
    }

    private fun showSummary() {
        toggleMainContent(false)

        with(binding.paybillSummary) {
            paybillSummaryCard.visibility = View.VISIBLE

            with(paybillViewModel) {
                paybillSummaryCard.setOnClickIcon { setEditing(true) }
                payFromAcct.text = channelsViewModel.activeAccount.value?.name
                recipient.text = businessNumber.value
                accountNo.text = accountNumber.value
                amountValue.text = amount.value
            }
        }

        binding.continueBtn.text = getString(R.string.pay_now)
    }

    private fun toggleMainContent(show: Boolean) {
        binding.billDetailsLayout.cardPaybillDetails.visibility = if (show) View.VISIBLE else View.GONE
        binding.saveBillLayout.cardSavePaybill.visibility = if (show) View.VISIBLE else View.GONE

        if (show) binding.continueBtn.visibility = View.VISIBLE
    }

    private fun setWatchers() {
        with(binding.billDetailsLayout) {
            businessNoInput.addTextChangedListener(businessNoWatcher)
            accountNoInput.addTextChangedListener(accountNoWatcher)
            amountInput.addTextChangedListener(amountWatcher)
        }

        binding.saveBillLayout.billNameInput.addTextChangedListener(nicknameWatcher)
    }

    private val amountWatcher: TextWatcher = object : TextWatcher {
        override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
        override fun afterTextChanged(editable: Editable) {}
        override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {
            paybillViewModel.setAmount(charSequence.toString().replace(",".toRegex(), ""))
        }
    }

    private val businessNoWatcher: TextWatcher = object : TextWatcher {
        override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
        override fun afterTextChanged(editable: Editable) {}
        override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {
            paybillViewModel.setBusinessNumber(charSequence.toString())
        }
    }

    private val accountNoWatcher: TextWatcher = object : TextWatcher {
        override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
        override fun afterTextChanged(editable: Editable) {}
        override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {
            paybillViewModel.setAccountNumber(charSequence.toString().replace(",".toRegex(), ""))
        }
    }

    private val nicknameWatcher: TextWatcher = object : TextWatcher {
        override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
        override fun afterTextChanged(editable: Editable) {}
        override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {
            paybillViewModel.setNickname(charSequence.toString())
        }
    }

    private fun validates(): Boolean {
        val businessNoError = paybillViewModel.businessNoError()
        val accountNoError = paybillViewModel.accountNoError()
        val amountError = paybillViewModel.amountError()
        val nickNameError = paybillViewModel.nameError()
        val saveBill = binding.saveBillLayout.saveBill.isChecked

        with(binding.billDetailsLayout) {
            businessNoInput.setState(businessNoError, if (businessNoError == null) AbstractStatefulInput.SUCCESS else AbstractStatefulInput.NONE)
            accountNoInput.setState(accountNoError, if (accountNoError == null) AbstractStatefulInput.SUCCESS else AbstractStatefulInput.NONE)
            amountInput.setState(amountError, if (amountError == null) AbstractStatefulInput.SUCCESS else AbstractStatefulInput.NONE)
        }

        if (saveBill)
            binding.saveBillLayout.billNameInput.setState(nickNameError, if (nickNameError == null) AbstractStatefulInput.SUCCESS else AbstractStatefulInput.NONE)

        return businessNoError == null && accountNoError == null && amountError == null && (if (saveBill) nickNameError == null else true)
    }

    private fun showIconsChooser() = with(binding) {
        toggleMainContent(false)
        binding.continueBtn.visibility = View.GONE

        with(paybillIconsLayout) {
            cardPaybillIcons.visibility = View.VISIBLE

            iconList.adapter = PaybillIconsAdapter(this@PaybillFragment)

            cardPaybillIcons.setOnClickIcon {
                cardPaybillIcons.visibility = View.GONE
                toggleMainContent(true)
            }
        }
    }


    override fun onPause() {
        super.onPause()
        paybillViewModel.setEditing(true)
    }

    override fun onResume() {
        super.onResume()
        //sometimes when navigating back from another fragment, the labels get all messed up
        with(binding.billDetailsLayout) {
            accountDropdown.setHint(getString(R.string.account_label))
            businessNoInput.setHint(getString(R.string.business_number_label))
            accountNoInput.setHint(getString(R.string.account_number_label))
            amountInput.setHint(getString(R.string.transfer_amount_label))

            businessNoInput.binding.inputLayout.apply {
                setEndIconDrawable(R.drawable.ic_twotone_chevron_right_24)
                setEndIconTintMode(PorterDuff.Mode.SRC_IN)
                setEndIconTintList(ColorStateList.valueOf(Color.WHITE))
            }
        }

        binding.paybillIconsLayout.cardPaybillIcons.visibility = View.GONE
    }

    companion object {
        const val UPDATE_BUSINESS_NO: String = "update_business_no"
    }

    override fun onSelectIcon(id: Int) {
        paybillViewModel.setIconDrawable(id)

        binding.paybillIconsLayout.cardPaybillIcons.visibility = View.GONE
        toggleMainContent(true)
    }
}
