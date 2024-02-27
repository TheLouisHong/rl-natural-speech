package dev.phyce.naturalspeech.downloader;

import dev.phyce.naturalspeech.downloader.DownloadTask;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.net.URI;

import java.nio.file.Path;

@Singleton
public class Downloader {

    @Inject
    private OkHttpClient httpClient;

    public DownloadTask create(HttpUrl url, Path destination) {
        return new DownloadTask(httpClient, destination, url, true);
    }


}