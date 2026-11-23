package com.example.workouttimer.ui

import android.app.Dialog
import android.os.Bundle
import android.view.WindowManager
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.workouttimer.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.URL
import javax.net.ssl.HttpsURLConnection

class FeedbackDialog : DialogFragment() {
    private val submission by lazy { ViewModelProvider(this)[FeedbackSubmission::class.java] }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val form = layoutInflater.inflate(R.layout.dialog_feedback, null)
        val content = form.findViewById<EditText>(R.id.feedback_content)
        val password = form.findViewById<EditText>(R.id.feedback_password)
        val passwordLabel = form.findViewById<TextView>(R.id.feedback_password_label)
        val status = form.findViewById<TextView>(R.id.feedback_status)
        var stateObserver: Job? = null
        return AlertDialog.Builder(requireContext())
            .setTitle(R.string.settings_feedback)
            .setView(form)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.feedback_send, null)
            .create().apply {
                window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
                setOnShowListener {
                    val send = getButton(AlertDialog.BUTTON_POSITIVE)
                    val cancel = getButton(AlertDialog.BUTTON_NEGATIVE)
                    send.setOnClickListener {
                        val message = content.text.toString().trim()
                        val editPassword = password.text.toString()
                        if (message.isEmpty() || editPassword.isBlank()) {
                            status.setText(R.string.feedback_required)
                            status.isVisible = true
                            return@setOnClickListener
                        }
                        submission.send(message, editPassword)
                    }
                    stateObserver?.cancel()
                    stateObserver = lifecycleScope.launch {
                        submission.state.collect { result ->
                            val sending = result == FeedbackSubmission.State.Sending
                            val sent = result == FeedbackSubmission.State.Sent
                            val showForm = !sent
                            this@FeedbackDialog.isCancelable = !sending
                            setCanceledOnTouchOutside(!sending)
                            send.isEnabled = !sending
                            cancel.isEnabled = !sending
                            cancel.isVisible = showForm
                            content.isEnabled = !sending
                            password.isEnabled = !sending
                            content.isVisible = showForm
                            password.isVisible = showForm
                            passwordLabel.isVisible = showForm
                            status.isVisible = result != FeedbackSubmission.State.Ready
                            when (result) {
                                FeedbackSubmission.State.Ready -> Unit
                                FeedbackSubmission.State.Sending -> status.setText(R.string.feedback_sending)
                                FeedbackSubmission.State.Sent -> {
                                    password.text.clear()
                                    content.text.clear()
                                    status.setText(R.string.feedback_sent)
                                    send.setText(android.R.string.ok)
                                    send.setOnClickListener { dismiss() }
                                }
                                FeedbackSubmission.State.Failed -> status.setText(R.string.feedback_failed)
                                FeedbackSubmission.State.RateLimited -> status.setText(R.string.feedback_rate_limited)
                            }
                        }
                    }
                }
            }
    }
}

internal class FeedbackSubmission : ViewModel() {
    enum class State { Ready, Sending, Sent, Failed, RateLimited }

    private val submission = MutableStateFlow(State.Ready)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val state = submission.asStateFlow()

    fun send(content: String, editPassword: String) {
        if (submission.value == State.Sending || submission.value == State.Sent) return
        submission.value = State.Sending
        scope.launch {
            submission.value = withContext(Dispatchers.IO) {
                try {
                    when (postFeedback(content, editPassword)) {
                        201 -> State.Sent
                        429 -> State.RateLimited
                        else -> State.Failed
                    }
                } catch (_: IOException) {
                    State.Failed
                }
            }
        }
    }

    override fun onCleared() {
        scope.cancel()
    }

    private fun postFeedback(content: String, editPassword: String): Int {
        val payload = JSONObject()
            .put("content", content)
            .put("userPassword", editPassword)
            .put("sourceAppId", 8)
            .toString().toByteArray(Charsets.UTF_8)
        val request = URL("https://api.jace-s.com/comments").openConnection() as HttpsURLConnection
        try {
            request.requestMethod = "POST"
            request.instanceFollowRedirects = false
            request.connectTimeout = 10_000
            request.readTimeout = 10_000
            request.doOutput = true
            request.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            request.setFixedLengthStreamingMode(payload.size)
            request.outputStream.use { it.write(payload) }
            return request.responseCode
        } finally {
            request.disconnect()
        }
    }
}
