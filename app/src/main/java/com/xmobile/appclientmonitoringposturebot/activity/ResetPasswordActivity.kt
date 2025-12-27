package com.xmobile.appclientmonitoringposturebot.activity

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.xmobile.appclientmonitoringposturebot.R
import com.xmobile.appclientmonitoringposturebot.databinding.ActivityResetPasswordBinding
import com.xmobile.appclientmonitoringposturebot.service.SupabaseProvider
import com.xmobile.appclientmonitoringposturebot.util.ValidateUtil
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.launch

class ResetPasswordActivity : AppCompatActivity() {
    private lateinit var binding: ActivityResetPasswordBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResetPasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initControl()
    }

    private fun initControl() {
        binding.edtPassword.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) binding.txtWarning.visibility = View.GONE
        }

        binding.edtConfirmPassword.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) binding.txtWarning.visibility = View.GONE
        }

        binding.btnConfirm.setOnClickListener {
            val password = binding.edtPassword.text.toString()
            val confirmPassword = binding.edtConfirmPassword.text.toString()

            if (validate(password, confirmPassword)) {
                lifecycleScope.launch {
                    try {
                        SupabaseProvider.client.auth.updateUser {
                            this.password = password
                        }

                        Toast.makeText(
                            this@ResetPasswordActivity,
                            "Đổi mật khẩu thành công",
                            Toast.LENGTH_SHORT
                        ).show()

                        finish()

                    } catch (e: Exception) {
                        binding.txtWarning.visibility = View.VISIBLE
                        binding.txtWarning.text =
                            e.message ?: "Không thể đổi mật khẩu, vui lòng thử lại"
                    }
                }
            }
        }

        binding.backLayout.setOnClickListener {
            finish()
        }
    }

    private fun validate(password: String, confirmPassword: String): Boolean {
        if (!ValidateUtil.emptyCheckPassword(password)) {
            binding.txtWarning.visibility = View.VISIBLE
            binding.txtWarning.text = "Vui lòng nhập mật khẩu"
            return false
        }else if (!ValidateUtil.conditionCheckPassword(password)) {
            binding.txtWarning.visibility = View.VISIBLE
            binding.txtWarning.text = "Mật khẩu phải có ít nhất 6 ký tự"
            return false
        }else if (!ValidateUtil.conditionCheckMatching(password, confirmPassword)) {
            binding.txtWarning.visibility = View.VISIBLE
            binding.txtWarning.text = "Mật khẩu không khớp"
            return false
        }
        return true
    }
}