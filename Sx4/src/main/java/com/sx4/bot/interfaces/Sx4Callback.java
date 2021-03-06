package com.sx4.bot.interfaces;

import java.io.IOException;

import com.sx4.bot.core.Sx4Bot;
import com.sx4.bot.core.Sx4CommandEventListener;
import com.sx4.bot.settings.Settings;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

@FunctionalInterface
public interface Sx4Callback extends Callback {
	
	public void onResponse(Response response) throws IOException;
	
	public default void onFailure(Call call, IOException e) {
		if (!call.isCanceled()) {
			e.printStackTrace();
			Sx4CommandEventListener.sendErrorMessage(Sx4Bot.getShardManager().getGuildById(Settings.SUPPORT_SERVER_ID).getTextChannelById(Settings.ERRORS_CHANNEL_ID), e, new Object[0]);
		}
	}
	
	public default void onResponse(Call call, Response response) throws IOException {
		try {
			this.onResponse(response);
		} catch (Exception e) {
			e.printStackTrace();
			Sx4CommandEventListener.sendErrorMessage(Sx4Bot.getShardManager().getGuildById(Settings.SUPPORT_SERVER_ID).getTextChannelById(Settings.ERRORS_CHANNEL_ID), e, new Object[0]);
		}
	}
}