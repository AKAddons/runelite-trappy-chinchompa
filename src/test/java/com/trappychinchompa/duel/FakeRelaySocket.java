package com.trappychinchompa.duel;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The socket seam: remembers opens, sends and closes; the test plays the relay. */
class FakeRelaySocket implements RelayClient.Socket
{
	static final Gson GSON = new Gson();
	static final java.lang.reflect.Type MAP = new TypeToken<Map<String, Object>>()
	{
	}.getType();

	final List<String> opened = new ArrayList<>();
	final List<Map<String, Object>> sent = new ArrayList<>();
	RelayClient.SocketListener listener;
	int closes;

	@Override
	public void open(String url, RelayClient.SocketListener listener)
	{
		opened.add(url);
		this.listener = listener;
	}

	@Override
	public void send(String text)
	{
		sent.add(GSON.fromJson(text, MAP));
	}

	@Override
	public void close()
	{
		closes++;
	}

	void up()
	{
		listener.onOpen();
	}

	void relay(Map<String, Object> msg)
	{
		listener.onText(GSON.toJson(msg));
	}

	void down()
	{
		listener.onClosed();
	}

	Map<String, Object> last()
	{
		return sent.get(sent.size() - 1);
	}

	List<Map<String, Object>> ofType(String t)
	{
		List<Map<String, Object>> out = new ArrayList<>();
		for (Map<String, Object> m : sent)
		{
			if (t.equals(m.get("t")))
			{
				out.add(m);
			}
		}
		return out;
	}

	static Map<String, Object> map(Object... kv)
	{
		Map<String, Object> m = new HashMap<>();
		for (int i = 0; i < kv.length; i += 2)
		{
			m.put((String) kv[i], kv[i + 1]);
		}
		return m;
	}
}
