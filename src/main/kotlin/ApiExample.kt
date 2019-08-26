/**
 * Sample Java code for youtube.playlistItems.delete
 * See instructions for running these code samples locally:
 * https://developers.google.com/explorer-help/guides/code_samples#java
 */

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.JsonFactory
import com.google.api.client.json.jackson2.JacksonFactory

import com.google.api.services.youtube.YouTube

import java.io.IOException
import java.security.GeneralSecurityException
import java.util.Arrays

object ApiExample {
    internal

    /**
     * Build and return an authorized API client service.
     *
     * @return an authorized API client service
     * @throws GeneralSecurityException, IOException
     */
    val service: YouTube by lazy {
            val httpTransport = GoogleNetHttpTransport.newTrustedTransport()
            return@lazy YouTube.Builder(httpTransport, JSON_FACTORY, null)
                .setApplicationName(APPLICATION_NAME)
                .build()
        }
}