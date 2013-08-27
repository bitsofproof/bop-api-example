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
import java.util.StringTokenizer;

import javax.jms.ConnectionFactory;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.Options;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import com.bitsofproof.supernode.api.AddressConverter;
import com.bitsofproof.supernode.api.AlertListener;
import com.bitsofproof.supernode.api.BCSAPI;
import com.bitsofproof.supernode.api.ExtendedKey;
import com.bitsofproof.supernode.api.JMSServerConnector;
import com.bitsofproof.supernode.api.Transaction;
import com.bitsofproof.supernode.api.TransactionInput;
import com.bitsofproof.supernode.api.TransactionListener;
import com.bitsofproof.supernode.api.TransactionOutput;
import com.bitsofproof.supernode.common.BloomFilter.UpdateMode;
import com.bitsofproof.supernode.common.ByteUtils;
import com.bitsofproof.supernode.common.ECKeyPair;
import com.bitsofproof.wallet.AccountManager;
import com.bitsofproof.wallet.ExtendedKeyAccountManager;
import com.bitsofproof.wallet.FileWallet;
import com.bitsofproof.wallet.KeyListAccountManager;

public class Simple
{
	private static int addressFlag;

	private static ConnectionFactory getConnectionFactory (String server, String user, String password)
	{
		ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory ();
		connectionFactory.setBrokerURL (server);
		connectionFactory.setUserName (user);
		connectionFactory.setPassword (password);
		return connectionFactory;
	}

	private static BCSAPI getServer (ConnectionFactory connectionFactory)
	{
		JMSServerConnector connector = new JMSServerConnector ();
		connector.setConnectionFactory (connectionFactory);
		connector.init ();
		return connector;
	}

	public static void main (String[] args)
	{
		final CommandLineParser parser = new GnuParser ();
		final Options gnuOptions = new Options ();
		gnuOptions.addOption ("h", "help", false, "I can't help you yet");
		gnuOptions.addOption ("s", "server", true, "Server URL");
		gnuOptions.addOption ("u", "user", true, "User");
		gnuOptions.addOption ("p", "password", true, "Password");

		System.out.println ("bop Enterprise Server Simple Client 1.1.6 (c) 2013 bits of proof zrt.");
		CommandLine cl = null;
		String url = null;
		String user = null;
		String password = null;
		try
		{
			cl = parser.parse (gnuOptions, args);
			url = cl.getOptionValue ('s');
			user = cl.getOptionValue ('u');
			password = cl.getOptionValue ('p');
		}
		catch ( org.apache.commons.cli.ParseException e )
		{
			e.printStackTrace ();
			System.exit (1);
		}
		if ( url == null || user == null || password == null )
		{
			System.err.println ("Need -s server -u user -p password");
			System.exit (1);
		}

		Security.addProvider (new BouncyCastleProvider ());
		BCSAPI api = getServer (getConnectionFactory (url, user, password));
		try
		{
			long start = System.currentTimeMillis ();
			api.ping (start);
			System.out.println ("Server round trip " + (System.currentTimeMillis () - start) + "ms");
			api.addAlertListener (new AlertListener ()
			{
				@Override
				public void alert (String s, int severity)
				{
					System.err.println ("ALERT: " + s);
				}
			});
			System.out.println ("Talking to " + (api.isProduction () ? "PRODUCTION" : "test") + " server");

			addressFlag = api.isProduction () ? 0x0 : 0x6f;
			FileWallet w = new FileWallet ("toy.wallet");
			AccountManager am = null;
			if ( !w.exists () )
			{
				System.console ().printf ("Enter passphrase: ");
				String passphrase = System.console ().readLine ();
				System.console ().printf ("Enter master (empty for new): ");
				String master = System.console ().readLine ();
				if ( master.equals ("") )
				{
					w.init (passphrase);
					w.unlock (passphrase);
					System.console ().printf ("First account: ");
					String account = System.console ().readLine ();
					am = w.createAccountManager (account);
					w.lock ();
					w.persist ();
				}
				else
				{
					StringTokenizer tokenizer = new StringTokenizer (master, "@");
					String key = tokenizer.nextToken ();
					long since = 0;
					if ( tokenizer.hasMoreElements () )
					{
						since = Long.parseLong (tokenizer.nextToken ()) * 1000;
					}
					w.init (passphrase, ExtendedKey.parse (key), true, since);
					w.unlock (passphrase);
					System.console ().printf ("First account: ");
					String account = System.console ().readLine ();
					am = w.createAccountManager (account);
					w.lock ();
					w.persist ();
					w.sync (api);
				}
			}
			else
			{
				w = FileWallet.read ("toy.wallet");
				w.sync (api);
				List<String> names = w.getAccountNames ();
				System.out.println ("Accounts:");
				System.out.println ("---------");
				String first = null;
				for ( String name : names )
				{
					System.out.println (name);
					if ( first == null )
					{
						first = name;
					}
				}
				System.out.println ("---------");
				am = w.getAccountManager (first);
				System.out.println ("Using account " + first);
			}
			api.registerTransactionListener (am);

			while ( true )
			{
				printMenu ();
				String answer = System.console ().readLine ();
				System.console ().printf ("\n");
				if ( answer.equals ("1") )
				{
					System.console ().printf ("The balance is: " + printXBT (am.getBalance ()) + "\n");
					System.console ().printf ("     confirmed: " + printXBT (am.getConfirmed ()) + "\n");
					System.console ().printf ("    receiveing: " + printXBT (am.getReceiving ()) + "\n");
					System.console ().printf ("        change: " + printXBT (am.getChange ()) + "\n");
					System.console ().printf ("(      sending: " + printXBT (am.getSending ()) + ")\n");
				}
				else if ( answer.equals ("2") )
				{
					am.getNextKey ();
					for ( byte[] a : am.getAddresses () )
					{
						System.console ().printf (AddressConverter.toSatoshiStyle (a, addressFlag) + " (" + ByteUtils.toHex (a) + ")\n");
					}
				}
				else if ( answer.equals ("3") )
				{
					ExtendedKeyAccountManager im = (ExtendedKeyAccountManager) am;
					System.console ().printf (im.getMaster ().serialize (api.isProduction ()) + "@" + im.getCreated () / 1000 + "\n");
				}
				else if ( answer.equals ("4") )
				{
					System.console ().printf ("Enter passphrase: ");
					String passphrase = System.console ().readLine ();
					w.unlock (passphrase);
					ExtendedKeyAccountManager im = (ExtendedKeyAccountManager) am;
					System.console ().printf (im.getMaster ().serialize (api.isProduction ()) + "@" + im.getCreated () / 1000 + "\n");
					w.lock ();
				}
				else if ( answer.equals ("5") )
				{
					System.console ().printf ("Enter passphrase: ");
					String passphrase = System.console ().readLine ();
					w.unlock (passphrase);
					System.console ().printf ("Receiver address: ");
					String address = System.console ().readLine ();
					System.console ().printf ("amount (XBT): ");
					long amount = parseXBT (System.console ().readLine ());
					byte[] a = AddressConverter.fromSatoshiStyle (address, addressFlag);
					Transaction spend = am.pay (a, amount, 10000);
					long fee = KeyListAccountManager.estimateFee (spend);
					spend = am.pay (a, amount, fee);
					System.out.println ("About to send " + printXBT (amount) + " to " + AddressConverter.toSatoshiStyle (a, 0x0) + " fee: " + fee);
					System.out.println ("inputs");
					for ( TransactionInput in : spend.getInputs () )
					{
						System.out.println (in.getSourceHash () + " " + in.getIx ());
					}
					System.out.println ("outputs");
					for ( TransactionOutput out : spend.getOutputs () )
					{
						System.out.println (AddressConverter.toSatoshiStyle (out.getOutputAddress (), 0x0) + " " + out.getValue ());
					}
					w.lock ();
					System.console ().printf ("Type yes to go: ");
					if ( System.console ().readLine ().equals ("yes") )
					{
						api.sendTransaction (spend);
						System.console ().printf ("Sent transaction: " + spend.getHash ());
					}
					else
					{
						System.console ().printf ("Nothing happened.");
					}
				}
				else if ( answer.equals ("6") )
				{
					System.console ().printf ("Address: ");
					List<byte[]> match = new ArrayList<byte[]> ();
					match.add (AddressConverter.fromSatoshiStyle (System.console ().readLine (), api.isProduction () ? 0x0 : 0x6f));
					api.scanTransactions (match, UpdateMode.all, 0, new TransactionListener ()
					{
						@Override
						public void process (Transaction t)
						{
							System.console ().printf ("Found transaction: " + t.getHash () + "\n");
						}
					});
				}
				else if ( answer.equals ("7") )
				{
					System.console ().printf ("Public key: ");
					ExtendedKey ek = ExtendedKey.parse (System.console ().readLine ());
					api.scanTransactions (ek, 0, 10, 0, new TransactionListener ()
					{
						@Override
						public void process (Transaction t)
						{
							System.console ().printf ("Found transaction: " + t.getHash () + "\n");
						}
					});
				}
				else if ( answer.equals ("8") )
				{
					ExtendedKeyAccountManager im = (ExtendedKeyAccountManager) am;
					im.dumpOutputs (System.out);
				}
				else if ( answer.equals ("9") )
				{
					System.console ().printf ("Enter passphrase: ");
					String passphrase = System.console ().readLine ();
					w.unlock (passphrase);
					ExtendedKeyAccountManager im = (ExtendedKeyAccountManager) am;
					im.dumpKeys (System.out);
					w.lock ();
				}
				else if ( answer.equals ("a") )
				{
					System.console ().printf ("Enter account name: ");
					String account = System.console ().readLine ();
					am = w.getAccountManager (account);
					api.registerTransactionListener (am);
				}
				else if ( answer.equals ("c") )
				{
					System.console ().printf ("Enter account name: ");
					String account = System.console ().readLine ();
					System.console ().printf ("Enter passphrase: ");
					String passphrase = System.console ().readLine ();
					w.unlock (passphrase);
					am = w.createAccountManager (account);
					System.out.println ("using account: " + account);
					w.lock ();
					w.persist ();
					api.registerTransactionListener (am);
				}
				else if ( answer.equals ("m") )
				{
					System.console ().printf ("Enter passphrase: ");
					String passphrase = System.console ().readLine ();
					w.unlock (passphrase);
					System.out.println (w.getMaster ().serialize (api.isProduction ()));
					System.out.println (w.getMaster ().getReadOnly ().serialize (true));
					w.lock ();
				}
				else if ( answer.equals ("s") )
				{
					System.console ().printf ("Enter private key: ");
					String key = System.console ().readLine ();
					ECKeyPair k = ECKeyPair.parseWIF (key);
					KeyListAccountManager alm = new KeyListAccountManager ();
					alm.addKey (k);
					alm.syncHistory (api);
					byte[] a = am.getNextKey ().getAddress ();
					Transaction t = alm.pay (a, alm.getBalance () - 10000, 10000L);
					long fee = KeyListAccountManager.estimateFee (t);
					t = alm.pay (a, alm.getBalance () - fee, fee);
					System.out.println ("About to sweep " + printXBT (alm.getBalance ()) + " to " + AddressConverter.toSatoshiStyle (a, 0x0) + " fee: " + fee);
					System.out.println ("inputs");
					for ( TransactionInput in : t.getInputs () )
					{
						System.out.println (in.getSourceHash () + " " + in.getIx ());
					}
					System.out.println ("outputs");
					for ( TransactionOutput out : t.getOutputs () )
					{
						System.out.println (AddressConverter.toSatoshiStyle (out.getOutputAddress (), 0x0) + " " + out.getValue ());
					}
					System.console ().printf ("Type yes to go: ");
					if ( System.console ().readLine ().equals ("yes") )
					{
						api.sendTransaction (t);
						System.console ().printf ("Sent transaction: " + t.getHash ());
					}
					else
					{
						System.console ().printf ("Nothing happened.");
					}
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
			System.exit (1);
		}
	}

	private static void printMenu ()
	{
		System.console ().printf ("\n");
		System.console ().printf ("1. get account balance\n");
		System.console ().printf ("2. show addresses\n");
		System.console ().printf ("3. show public key\n");
		System.console ().printf ("4. show private key\n");
		System.console ().printf ("5. pay\n");
		System.console ().printf ("6. transactions for an address\n");
		System.console ().printf ("7. transactions for an extended public key\n");
		System.console ().printf ("8. dump walet\n");
		System.console ().printf ("9. dump private keys\n");
		System.console ().printf ("a. change account\n");
		System.console ().printf ("c. create account\n");
		System.console ().printf ("m. show master\n");
		System.console ().printf ("s. sweep\n");

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
}
