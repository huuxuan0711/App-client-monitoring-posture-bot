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
import com.xmobile.appclientmonitoringposturebot.databinding.ActivityForgetPasswordBinding
import com.xmobile.appclientmonitoringposturebot.service.SupabaseProvider
import com.xmobile.appclientmonitoringposturebot.util.ValidateUtil
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.launch

class ForgetPasswordActivity : AppCompatActivity() {
    private lateinit var binding: ActivityForgetPasswordBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityForgetPasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initControl()
    }

    private fun initControl() {
        binding.edtEmail.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) binding.txtWarning.visibility = View.GONE
        }

        binding.btnSend.setOnClickListener {
            val email = binding.edtEmail.text.toString()
            if (validate(email)) sendEmail(email)
        }
    }

    private fun validate(email: String): Boolean {
        if (!ValidateUtil.emptyCheckEmail(email)) {
            binding.txtWarning.visibility = View.VISIBLE
            binding.txtWarning.text = "Vui lòng nhập email"
            return false
        }else if (!ValidateUtil.formatCheck(email)) {
            binding.txtWarning.visibility = View.VISIBLE
            binding.txtWarning.text = "Email không đúng định dạng"
            return false
        }
        return true
    }

    private fun sendEmail(email: String){
        lifecycleScope.launch {
            try {
                SupabaseProvider.client.auth.resetPasswordForEmail(email) // trả về Unit
                Toast.makeText(
                    this@ForgetPasswordActivity,
                    "Email reset mật khẩu đã được gửi",
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                Toast.makeText(
                    this@ForgetPasswordActivity,
                    "Lỗi: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}