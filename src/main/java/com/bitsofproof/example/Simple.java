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

import java.math.BigDecimal;
import java.security.Security;
import java.text.NumberFormat;
import java.text.ParseException;

import javax.jms.ConnectionFactory;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.fusesource.stomp.jms.StompJmsConnectionFactory;

import com.bitsofproof.supernode.api.AccountListener;
import com.bitsofproof.supernode.api.AccountManager;
import com.bitsofproof.supernode.api.AddressConverter;
import com.bitsofproof.supernode.api.BCSAPI;
import com.bitsofproof.supernode.api.ClientBusAdaptor;
import com.bitsofproof.supernode.api.Key;
import com.bitsofproof.supernode.api.Transaction;
import com.bitsofproof.supernode.api.Wallet;

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
			if ( api.isProduction () )
			{
				System.err.println ("You do not want to do this.");
				System.exit (1);
			}
			Wallet w = api.getWallet ("toy.wallet", "password");
			AccountManager am = w.getAccountManager ("one");
			am.addAccountListener (new AccountListener ()
			{
				@Override
				public void accountChanged (AccountManager account)
				{
					System.console ().printf ("\n\nBalance change: " + printXBT (account.getBalance ()));
					printMenu ();
					System.console ().flush ();
				}
			});
			while ( true )
			{
				printMenu ();
				String answer = System.console ().readLine ();
				System.console ().printf ("\n");
				if ( answer.equals ("1") )
				{
					System.console ().printf ("The balance is: " + printXBT (am.getBalance ()) + "\n");
				}
				else if ( answer.equals ("2") )
				{
					for ( byte[] a : am.getAddresses () )
					{
						System.console ().printf (AddressConverter.toSatoshiStyle (a, addressFlag) + "\n");
					}
				}
				else if ( answer.equals ("3") )
				{
					Key key = am.getNextKey ();
					w.persist ();
					System.console ().printf (AddressConverter.toSatoshiStyle (key.getAddress (), addressFlag) + "\n");
				}
				else if ( answer.equals ("4") )
				{
					System.console ().printf ("Receiver address: ");
					String address = System.console ().readLine ();
					System.console ().printf ("amount (XBT): ");
					long amount = parseXBT (System.console ().readLine ());
					Transaction spend = am.pay (AddressConverter.fromSatoshiStyle (address, addressFlag), amount, 10000);
					api.sendTransaction (spend);
					w.persist ();
				}
				else
				{
					System.exit (0);
				}
			}
		}
		catch ( Exception e )
		{
			System.err.println ("Something went wrong");
			e.printStackTrace ();
		}
	}

	private static void printMenu ()
	{
		System.console ().printf ("\n");
		System.console ().printf ("1. get account balance\n");
		System.console ().printf ("2. show addresses\n");
		System.console ().printf ("3. get a new address\n");
		System.console ().printf ("4. pay\n");

		System.console ().printf ("Your choice: ");
	}

	public static String printXBT (long n)
	{
		BigDecimal xbt = BigDecimal.valueOf (n).divide (BigDecimal.valueOf (100));
		return NumberFormat.getNumberInstance ().format (xbt) + " XBT";
	}

	public static long parseXBT (String s) throws ParseException
	{
		Number n = NumberFormat.getNumberInstance ().parse (s);
		if ( n instanceof BigDecimal )
		{
			return ((BigDecimal) n).multiply (BigDecimal.valueOf (100)).longValue ();
		}
		else
		{
			return n.longValue () * 100;
		}
	}

	private static final int addressFlag = 0x6f;
}
