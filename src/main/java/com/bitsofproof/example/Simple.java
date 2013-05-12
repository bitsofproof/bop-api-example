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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;

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
			long start = System.currentTimeMillis ();
			api.ping (start);
			System.out.println ("Server round trip " + (System.currentTimeMillis () - start) + "ms");

			Wallet w = api.getWallet ("toy.wallet", "password");
			AccountManager am = w.getAccountManager ("one");

			final Semaphore update = new Semaphore (0);
			final List<Transaction> received = new ArrayList<Transaction> ();
			am.addAccountListener (new AccountListener ()
			{
				@Override
				public void accountChanged (AccountManager account, Transaction t)
				{
					if ( t != null ) // t is null change through new blocks
					{
						received.add (t);
					}
					update.release ();
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
					System.console ().printf ("       settled: " + printXBT (am.getSettled ()) + "\n");
					System.console ().printf ("       sending: " + printXBT (am.getSending ()) + "\n");
					System.console ().printf ("    receiveing: " + printXBT (am.getReceiving ()) + "\n");
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
					for ( Transaction t : am.getTransactions () )
					{
						System.console ().printf (t.getHash () + (t.getBlockHash () != null ? " settled " : " pending ") + "\n");
					}
				}
				else if ( answer.equals ("5") )
				{
					update.acquireUninterruptibly ();
					update.drainPermits ();
					for ( Transaction t : received )
					{
						System.console ().printf ("Received transaction : " + t.getHash ());
					}
					System.console ().printf ("The balance is: " + printXBT (am.getBalance ()) + "\n");
				}
				else if ( answer.equals ("6") )
				{
					System.console ().printf ("Receiver address: ");
					String address = System.console ().readLine ();
					System.console ().printf ("amount (XBT): ");
					long amount = parseXBT (System.console ().readLine ());
					Transaction spend = am.pay (AddressConverter.fromSatoshiStyle (address, addressFlag), amount, 10000);
					api.sendTransaction (spend);
					System.console ().printf ("Sent transaction: " + spend.getHash ());
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
		System.console ().printf ("4. transaction history\n");
		System.console ().printf ("5. wait for update\n");
		System.console ().printf ("6. pay\n");

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
