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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.security.Security;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.Options;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import com.bitsofproof.supernode.account.ConfirmationManager;
import com.bitsofproof.supernode.account.ExtendedKeyAccountManager;
import com.bitsofproof.supernode.account.KeyListAccountManager;
import com.bitsofproof.supernode.account.PaymentOptions;
import com.bitsofproof.supernode.account.TransactionFactory;
import com.bitsofproof.supernode.api.Address;
import com.bitsofproof.supernode.api.AlertListener;
import com.bitsofproof.supernode.api.BCSAPI;
import com.bitsofproof.supernode.api.Transaction;
import com.bitsofproof.supernode.api.TransactionInput;
import com.bitsofproof.supernode.api.TransactionListener;
import com.bitsofproof.supernode.api.TransactionOutput;
import com.bitsofproof.supernode.common.ECKeyPair;
import com.bitsofproof.supernode.common.ExtendedKey;
import com.bitsofproof.supernode.connector.BCSAPIConnector;
import com.bitsofproof.supernode.jms.JMSConnectorFactory;
import com.bitsofproof.supernode.misc.SimpleFileWallet;

public class Simple
{
	private static BCSAPI getServer (String server, String user, String password)
	{
		BCSAPIConnector api = new BCSAPIConnector ();
		api.setConnectionFactory (new JMSConnectorFactory (server, user, password));
		api.init ();
		return api;
	}

	public static void main (String[] args)
	{
		Security.addProvider (new BouncyCastleProvider ());

		final CommandLineParser parser = new GnuParser ();
		final Options gnuOptions = new Options ();
		gnuOptions.addOption ("h", "help", false, "I can't help you yet");
		gnuOptions.addOption ("s", "server", true, "Server URL");
		gnuOptions.addOption ("u", "user", true, "User");
		gnuOptions.addOption ("p", "password", true, "Password");

		System.out.println ("BOP Bitcoin Server Simple Client 3.4.0 (c) 2013-2014 bits of proof zrt.");
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

		BCSAPI api = getServer (url, user, password);
		try
		{
			BufferedReader input = new BufferedReader (new InputStreamReader (System.in));
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
			ConfirmationManager confirmationManager = new ConfirmationManager ();
			confirmationManager.init (api, 144);
			System.out.printf ("Please enter wallet name: ");
			String wallet = input.readLine ();
			SimpleFileWallet w = new SimpleFileWallet (wallet + ".wallet");
			TransactionFactory am = null;
			if ( !w.exists () )
			{
				System.out.printf ("Enter passphrase: ");
				String passphrase = input.readLine ();
				System.out.printf ("Enter master (empty for new): ");
				String master = input.readLine ();
				if ( master.equals ("") )
				{
					w.init (passphrase);
					w.unlock (passphrase);
					System.out.printf ("First account: ");
					String account = input.readLine ();
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
					System.out.printf ("First account: ");
					String account = input.readLine ();
					am = w.createAccountManager (account);
					w.lock ();
					w.persist ();
					w.sync (api);
				}
			}
			else
			{
				w = SimpleFileWallet.read (wallet + ".wallet");
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
			confirmationManager.addAccount (am);
			api.registerTransactionListener (am);

			while ( true )
			{
				printMenu ();
				String answer = input.readLine ();
				System.out.printf ("\n");
				if ( answer.equals ("1") )
				{
					System.out.printf ("The balance is: " + printBit (am.getBalance ()) + "\n");
					System.out.printf ("     confirmed: " + printBit (am.getConfirmed ()) + "\n");
					System.out.printf ("    receiveing: " + printBit (am.getReceiving ()) + "\n");
					System.out.printf ("        change: " + printBit (am.getChange ()) + "\n");
					System.out.printf ("(      sending: " + printBit (am.getSending ()) + ")\n");
				}
				else if ( answer.equals ("2") )
				{
					for ( Address a : am.getAddresses () )
					{
						System.out.printf (a + "\n");
					}
				}
				else if ( answer.equals ("3") )
				{
					ExtendedKeyAccountManager im = (ExtendedKeyAccountManager) am;
					System.out.printf (im.getMaster ().serialize (api.isProduction ()) + "@" + im.getCreated () / 1000 + "\n");
				}
				else if ( answer.equals ("4") )
				{
					System.out.printf ("Enter passphrase: ");
					String passphrase = input.readLine ();
					w.unlock (passphrase);
					ExtendedKeyAccountManager im = (ExtendedKeyAccountManager) am;
					System.out.printf (im.getMaster ().serialize (api.isProduction ()) + "@" + im.getCreated () / 1000 + "\n");
					w.lock ();
				}
				else if ( answer.equals ("5") )
				{
					System.out.printf ("Enter passphrase: ");
					String passphrase = input.readLine ();
					w.unlock (passphrase);
					System.out.printf ("Number of recipients");
					Integer nr = Integer.valueOf (input.readLine ());
					Transaction spend;
					if ( nr.intValue () == 1 )
					{
						System.out.printf ("Receiver address: ");
						String address = input.readLine ();
						System.out.printf ("amount (Bit): ");
						long amount = parseBit (input.readLine ());
						spend = am.pay (Address.fromSatoshiStyle (address), amount);
						System.out.println ("About to send " + printBit (amount) + " to " + address);
					}
					else
					{
						List<Address> addresses = new ArrayList<> ();
						List<Long> amounts = new ArrayList<> ();
						long total = 0;
						for ( int i = 0; i < nr; ++i )
						{
							System.out.printf ("Receiver address: ");
							String address = input.readLine ();
							addresses.add (Address.fromSatoshiStyle (address));
							System.out.printf ("amount (Bit): ");
							long amount = parseBit (input.readLine ());
							amounts.add (amount);
							total += amount;
						}
						spend = am.pay (addresses, amounts);
						System.out.println ("About to send " + printBit (total));
					}
					System.out.println ("inputs");
					for ( TransactionInput in : spend.getInputs () )
					{
						System.out.println (in.getSourceHash () + " " + in.getIx ());
					}
					System.out.println ("outputs");
					for ( TransactionOutput out : spend.getOutputs () )
					{
						System.out.println (out.getOutputAddress () + " " + printBit (out.getValue ()));
					}
					w.lock ();
					System.out.printf ("Type yes to go: ");
					if ( input.readLine ().equals ("yes") )
					{
						api.sendTransaction (spend);
						System.out.printf ("Sent transaction: " + spend.getHash ());
					}
					else
					{
						System.out.printf ("Nothing happened.");
					}
				}
				else if ( answer.equals ("6") )
				{
					System.out.printf ("Address: ");
					Set<Address> match = new HashSet<Address> ();
					match.add (Address.fromSatoshiStyle (input.readLine ()));
					api.scanTransactionsForAddresses (match, 0, new TransactionListener ()
					{
						@Override
						public boolean process (Transaction t)
						{
							System.out.printf ("Found transaction: " + t.getHash () + "\n");
							return true;
						}
					});
				}
				else if ( answer.equals ("7") )
				{
					System.out.printf ("Public key: ");
					ExtendedKey ek = ExtendedKey.parse (input.readLine ());
					api.scanTransactions (ek, 0, 10, 0, new TransactionListener ()
					{
						@Override
						public boolean process (Transaction t)
						{
							System.out.printf ("Found transaction: " + t.getHash () + "\n");
							return true;
						}
					});
				}
				else if ( answer.equals ("a") )
				{
					System.out.printf ("Enter account name: ");
					String account = input.readLine ();
					am = w.getAccountManager (account);
					api.registerTransactionListener (am);
					confirmationManager.addAccount (am);
				}
				else if ( answer.equals ("c") )
				{
					System.out.printf ("Enter account name: ");
					String account = input.readLine ();
					System.out.printf ("Enter passphrase: ");
					String passphrase = input.readLine ();
					w.unlock (passphrase);
					am = w.createAccountManager (account);
					System.out.println ("using account: " + account);
					w.lock ();
					w.persist ();
					api.registerTransactionListener (am);
					confirmationManager.addAccount (am);
				}
				else if ( answer.equals ("m") )
				{
					System.out.printf ("Enter passphrase: ");
					String passphrase = input.readLine ();
					w.unlock (passphrase);
					System.out.println (w.getMaster ().serialize (api.isProduction ()));
					System.out.println (w.getMaster ().getReadOnly ().serialize (true));
					w.lock ();
				}
				else if ( answer.equals ("s") )
				{
					System.out.printf ("Enter private key: ");
					String key = input.readLine ();
					ECKeyPair k = ECKeyPair.parseWIF (key);
					KeyListAccountManager alm = new KeyListAccountManager ();
					alm.addKey (k);
					alm.syncHistory (api);
					Address a = am.getNextReceiverAddress ();
					Transaction t = alm.pay (a, alm.getBalance (), PaymentOptions.receiverPaysFee);
					System.out.println ("About to sweep " + printBit (alm.getBalance ()) + " to " + a);
					System.out.println ("inputs");
					for ( TransactionInput in : t.getInputs () )
					{
						System.out.println (in.getSourceHash () + " " + in.getIx ());
					}
					System.out.println ("outputs");
					for ( TransactionOutput out : t.getOutputs () )
					{
						System.out.println (out.getOutputAddress () + " " + printBit (out.getValue ()));
					}
					System.out.printf ("Type yes to go: ");
					if ( input.readLine ().equals ("yes") )
					{
						api.sendTransaction (t);
						System.out.printf ("Sent transaction: " + t.getHash ());
					}
					else
					{
						System.out.printf ("Nothing happened.");
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
		System.out.printf ("\n");
		System.out.printf ("1. get account balance\n");
		System.out.printf ("2. show addresses\n");
		System.out.printf ("3. show public key\n");
		System.out.printf ("4. show private key\n");
		System.out.printf ("5. pay\n");
		System.out.printf ("6. transactions for an address\n");
		System.out.printf ("7. transactions for an extended public key\n");
		System.out.printf ("a. change account\n");
		System.out.printf ("c. create account\n");
		System.out.printf ("m. show master\n");
		System.out.printf ("s. sweep\n");

		System.out.printf ("Your choice: ");
	}

	public static String printBit (long n)
	{
		BigDecimal xbt = BigDecimal.valueOf (n).divide (BigDecimal.valueOf (100));
		return NumberFormat.getNumberInstance ().format (xbt) + " bit";
	}

	public static long parseBit (String s) throws ParseException
	{
		Number n = NumberFormat.getNumberInstance ().parse (s);
		if ( n instanceof Double )
		{
			return (long) (n.doubleValue () * 100.0);
		}
		else
		{
			return n.longValue () * 100;
		}
	}
}
