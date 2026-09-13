package com.trappychinchompa.duel;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/** The relay socket over RuneLite's OkHttp client. */
public final class OkHttpSocket implements RelayClient.Socket
{
	private final OkHttpClient http;
	private WebSocket socket;

	public OkHttpSocket(OkHttpClient http)
	{
		this.http = http;
	}

	@Override
	public void open(String url, RelayClient.SocketListener listener)
	{
		close();
		socket = http.newWebSocket(new Request.Builder().url(url).build(), new WebSocketListener()
		{
			@Override
			public void onOpen(WebSocket webSocket, Response response)
			{
				listener.onOpen();
			}

			@Override
			public void onMessage(WebSocket webSocket, String text)
			{
				listener.onText(text);
			}

			@Override
			public void onClosed(WebSocket webSocket, int code, String reason)
			{
				listener.onClosed();
			}

			@Override
			public void onFailure(WebSocket webSocket, Throwable t, Response response)
			{
				listener.onClosed();
			}
		});
	}

	@Override
	public void send(String text)
	{
		WebSocket s = socket;
		if (s != null)
		{
			s.send(text);
		}
	}

	@Override
	public void close()
	{
		WebSocket s = socket;
		socket = null;
		if (s != null)
		{
			s.close(1000, "bye");
		}
	}
}
