package androidx.biometric
import java.util.concurrent.Executor
class BiometricManager private constructor() {
    object Authenticators { const val BIOMETRIC_WEAK = 0xFF; const val DEVICE_CREDENTIAL = 0x8000 }
    fun canAuthenticate(a: Int): Int = 0
    companion object { @JvmStatic fun from(c: android.content.Context) = BiometricManager() }
}
class BiometricPrompt(a: androidx.fragment.app.FragmentActivity, e: Executor, cb: AuthenticationCallback) {
    abstract class AuthenticationCallback {
        open fun onAuthenticationSucceeded(result: AuthenticationResult) {}
        open fun onAuthenticationError(errorCode: Int, errString: CharSequence) {}
        open fun onAuthenticationFailed() {}
    }
    class AuthenticationResult
    class PromptInfo {
        class Builder {
            fun setTitle(s: CharSequence) = this
            fun setSubtitle(s: CharSequence?) = this
            fun setAllowedAuthenticators(a: Int) = this
            fun setNegativeButtonText(s: CharSequence) = this
            fun build() = PromptInfo()
        }
    }
    fun authenticate(i: PromptInfo) {}
}
