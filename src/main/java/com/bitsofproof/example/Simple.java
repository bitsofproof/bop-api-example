/*
 * Copyright 2013 bits of proof zrt.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.bitsofproof.example;

import java.security.Security;

import javax.jms.ConnectionFactory;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.fusesource.stomp.jms.StompJmsConnectionFactory;

import com.bitsofproof.supernode.api.BCSAPI;
import com.bitsofproof.supernode.api.BCSAPIException;
import com.bitsofproof.supernode.api.ClientBusAdaptor;

public class Simple
{
	private static ConnectionFactory getConnectionFactory ()
	{
		StompJmsConnectionFactory connectionFactory = new StompJmsConnectionFactory ();
		connectionFactory.setBrokerURI ("tcp://test-api.bitsofproof.com:61613");
		connectionFactory.setUsername ("demo");
		connectionFactory.setPassword ("password");
		return connectionFactory;
	}

	private static BCSAPI getServer (ConnectionFactory connectionFactory)
	{
		ClientBusAdaptor api = new ClientBusAdaptor ();
		api.setConnectionFactory (connectionFactory);
		api.setClientId ("simple");
		api.init ();
		return api;
	}

	public static void main (String[] args)
	{
		System.out.println ("bop Enterprise Server Simple Client 1.0 (c) 2013 bits of proof zrt.");
		Security.addProvider (new BouncyCastleProvider ());

		BCSAPI api = getServer (getConnectionFactory ());

		try
		{
			if ( api.getBlockHeader ("000000000933ea01ad0ee984209779baaec3ced90fa3f408719526f8d77f4943") != null )
			{
				System.out.println ("We are talking to the TEST server.");
			}
		}
		catch ( BCSAPIException e )
		{
			System.err.println ("Something went wrong");
			e.printStackTrace ();
		}
	}
}
