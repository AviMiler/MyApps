package com.myappstore.smsforwarder.engine

import android.content.Context
import android.telephony.SmsManager
import com.myappstore.smsforwarder.R
import com.myappstore.smsforwarder.data.Reason

/** Human readable explanations for log reasons, shared by the UI and notifications. */
object Texts {

    fun reason(context: Context, reason: Int, detail: String?): String = when (reason) {
        Reason.APP_OFF -> context.getString(R.string.reason_app_off)
        Reason.PAUSED -> context.getString(R.string.reason_paused)
        Reason.QUIET_HOURS -> context.getString(R.string.reason_quiet)
        Reason.REST_DAY -> context.getString(R.string.reason_rest)
        Reason.OUT_OF_SCHEDULE -> context.getString(R.string.reason_schedule)
        Reason.MISSING_KEYWORD -> context.getString(R.string.reason_missing_keyword)
        Reason.EXCLUDED_KEYWORD -> context.getString(R.string.reason_excluded_keyword)
        Reason.NOT_A_CODE -> context.getString(R.string.reason_not_code)
        Reason.WRONG_SIM -> context.getString(R.string.reason_wrong_sim)
        Reason.SELF_LOOP -> context.getString(R.string.reason_self_loop)
        Reason.DUPLICATE -> context.getString(R.string.reason_duplicate)
        Reason.BURST -> context.getString(R.string.reason_burst)
        Reason.DAILY_LIMIT -> context.getString(R.string.reason_daily_limit)
        Reason.NO_PERMISSION -> context.getString(R.string.reason_no_permission)
        Reason.SEND_ERROR -> sendError(context, detail)
        Reason.NOT_DELIVERED -> context.getString(R.string.reason_not_delivered)
        Reason.USER_CANCELLED -> context.getString(R.string.reason_cancelled)
        Reason.UNDO_WINDOW -> context.getString(R.string.reason_undo_window)
        Reason.RETRY -> context.getString(R.string.reason_retry, sendError(context, detail))
        Reason.NO_REPLY_TARGET -> context.getString(R.string.reason_no_reply_target)
        else -> ""
    }

    private fun sendError(context: Context, detail: String?): String {
        val code = detail?.toIntOrNull() ?: return if (detail.isNullOrBlank()) {
            context.getString(R.string.error_generic)
        } else {
            context.getString(R.string.error_with_detail, detail)
        }
        return when (code) {
            SmsManager.RESULT_ERROR_GENERIC_FAILURE -> context.getString(R.string.error_generic_failure)
            SmsManager.RESULT_NO_DEFAULT_SMS_APP -> context.getString(R.string.error_no_sim_chosen)
            SmsManager.RESULT_USER_NOT_ALLOWED -> context.getString(R.string.error_user_not_allowed)
            SmsManager.RESULT_NETWORK_REJECT,
            SmsManager.RESULT_NETWORK_ERROR,
            -> context.getString(R.string.error_network_reject)
            SmsManager.RESULT_INVALID_ARGUMENTS -> context.getString(R.string.error_bad_number)
            SmsManager.RESULT_INVALID_SMSC_ADDRESS -> context.getString(R.string.error_smsc)
            SmsManager.RESULT_ERROR_RADIO_OFF -> context.getString(R.string.error_radio_off)
            SmsManager.RESULT_ERROR_NO_SERVICE -> context.getString(R.string.error_no_service)
            SmsManager.RESULT_RADIO_NOT_AVAILABLE -> context.getString(R.string.error_no_service)
            SmsManager.RESULT_ERROR_LIMIT_EXCEEDED -> context.getString(R.string.error_limit)
            SmsManager.RESULT_ERROR_SHORT_CODE_NOT_ALLOWED,
            SmsManager.RESULT_ERROR_SHORT_CODE_NEVER_ALLOWED,
            -> context.getString(R.string.error_short_code)
            SmsManager.RESULT_ERROR_FDN_CHECK_FAILURE -> context.getString(R.string.error_fdn)
            else -> context.getString(R.string.error_code, code)
        }
    }
}
