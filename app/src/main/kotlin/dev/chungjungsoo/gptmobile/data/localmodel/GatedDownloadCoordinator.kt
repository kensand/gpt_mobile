package dev.chungjungsoo.gptmobile.data.localmodel

import dev.chungjungsoo.gptmobile.data.catalog.CatalogEntry
import dev.chungjungsoo.gptmobile.data.huggingface.HuggingFaceTokenStore
import dev.chungjungsoo.gptmobile.data.huggingface.HuggingFaceUrls

sealed class GatedDownloadStep {
    data object Proceed : GatedDownloadStep()
    data class NeedsSignIn(val sessionExpired: Boolean) : GatedDownloadStep()
    data class NeedsLicense(val modelPageUrl: String) : GatedDownloadStep()
    data object OAuthNotConfigured : GatedDownloadStep()
    data object Error : GatedDownloadStep()
}

class GatedDownloadCoordinator(
    private val tokenStore: HuggingFaceTokenStore,
    private val prober: LocalModelDownloadProber,
    private val isOAuthConfigured: () -> Boolean
) {
    suspend fun resolve(entry: CatalogEntry): GatedDownloadStep {
        if (!entry.isGated) return GatedDownloadStep.Proceed

        val token = tokenStore.readAccessToken()
        val statusCode = prober.probe(entry.downloadUrl, token)
        val decision = GatedDownloadAuth.decide(
            statusCode = statusCode,
            hasToken = token != null,
            isGated = true
        )
        return when (decision.action) {
            GatedDownloadAction.PROCEED -> GatedDownloadStep.Proceed

            GatedDownloadAction.NEEDS_SIGN_IN -> {
                if (decision.isSessionExpired) {
                    tokenStore.clear()
                }
                if (!isOAuthConfigured()) {
                    GatedDownloadStep.OAuthNotConfigured
                } else {
                    GatedDownloadStep.NeedsSignIn(sessionExpired = decision.isSessionExpired)
                }
            }

            GatedDownloadAction.NEEDS_LICENSE -> {
                val modelPageUrl = HuggingFaceUrls.modelPageUrl(entry.downloadUrl)
                    ?: return GatedDownloadStep.Error
                GatedDownloadStep.NeedsLicense(modelPageUrl)
            }

            GatedDownloadAction.ERROR -> GatedDownloadStep.Error
        }
    }
}
