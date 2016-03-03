package cz.majncraft.cartDNS;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.text.Normalizer;
import java.text.Normalizer.Form;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import com.github.catageek.ByteCartAPI.ByteCartAPI;
import com.github.catageek.ByteCartAPI.AddressLayer.Address;
import com.github.catageek.ByteCartAPI.AddressLayer.Resolver;
import com.github.catageek.ByteCartAPI.Event.SignCreateEvent;
import com.github.catageek.ByteCartAPI.Event.SignRemoveEvent;
import com.github.catageek.ByteCartAPI.Event.UpdaterClearStationEvent;
import com.github.catageek.ByteCartAPI.Event.UpdaterSetStationEvent;
import com.github.catageek.ByteCartAPI.Event.UpdaterPassStationEvent;
import com.github.catageek.ByteCartAPI.Signs.Station;

import code.husky.Database;
import code.husky.mysql.MySQL;
import code.husky.sqlite.SQLite;


public class CartDNS extends JavaPlugin implements Resolver,Listener {
	Database mysql;
	Connection con;
	Statement s;
	boolean err=false;
	@Override
	public void onLoad()
	{
		firstrun();
		if (sql.equalsIgnoreCase("mysql")) {
			mysql = new MySQL(this, host, port, database, user, password);
		}
		else {
			mysql = new SQLite(this, database);
		}
		con=mysql.openConnection();
		try {
			s = con.createStatement();
			s.execute("create table if not exists cart_dns (ip varchar(11) not null primary key,username varchar(20) not null,uuid varchar(128) not null,name varchar(20) not null unique)");
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
	@Override
	public void onEnable()
	{
		ByteCartAPI.setResolver(this);
		getServer().getPluginManager().registerEvents(this, this);
	}

	@Override
	public boolean onCommand(CommandSender a, Command b , String c, String[] d)
	{
		return onCommand(a,b,c,d,0);
	}

	public boolean onCommand(CommandSender a, Command b , String c, String[] d,int n)
	{
		if(c.toLowerCase().equals("dns"))
		{
			if(d.length==0)
			{
				a.sendMessage("/dns create [hostname] [IP] - create DNS record.");
				a.sendMessage("/dns lookup [hostname] - info about hostname.");
				a.sendMessage("/dns lookup [IP]       - info about IP.");
				a.sendMessage("/dns remove [hostname] - remove DNS record.");
				a.sendMessage("/dns list [page] - list all DNS records.");
				return true;
			}
			else if(d.length>=1)
			{
			// Strip diacritics from Station name
				try{
					switch(d[0])
					{
					case "create":
						if (a.hasPermission("cartdns.manager")) {
							d[1] = Normalizer.normalize(d[1], Form.NFD).replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
							if(!safeName(d[1]))
							{
								a.sendMessage("[CartDNS] No hacking this time");
								return true;
							}
							else if(d.length<3 || d[2].equals("") || d[1].length()>20)
							{
								a.sendMessage("[CartDNS] Wrong IP/Name or not enought args.");
								return true;
							}
							if(!existEntryByName(getName(d,1,1), a))
							{
								String uu="Console",user="Console";
								if((a instanceof Player))
								{
									uu=((Player)a).getUniqueId().toString();
									user=((Player)a).getName();
								}
								createEntry(getName(d,1,1), safeIP(d[2]), uu, user);
								a.sendMessage("[CartDNS] Added");
							}
							return true;
						}
						else {
							a.sendMessage("[CartDNS] You don't have permission to use this command.");
							return true;
						}
					case "remove":
						if (a.hasPermission("cartdns.manager")) {
							d[1] = Normalizer.normalize(d[1], Form.NFD).replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
							if(!safeName(d[1]))
							{
								a.sendMessage("[CartDNS] No hacking this time");
								return true;
							}
							if(removeEntry(getName(d,1,1))) {
								a.sendMessage("[CartDNS] Deleted");
								return true;
							}
						}
						else {
							a.sendMessage("[CartDNS] You don't have permission to use this command.");
							return true;
						}
					case "list":
						if((a.hasPermission("cartdns.manager")|| a.hasPermission("cartdns.user")))
						{
							if(d.length==0)
							{
								a.sendMessage("/dns list [page] - list all DNS records.");
							}
							else if(d.length>=1)
							{
								int listc=0;
								if(d.length>=2 && d[1].matches("-?\\d+"))
								{
									listc=Integer.parseInt(d[1]);
								}
								try {
								ResultSet res=s.executeQuery("SELECT * FROM `cart_dns` LIMIT "+(listc*10)+", "+(listc*10+10));
									if(!res.next())
										a.sendMessage("No DNS records");
									else
									{
										a.sendMessage("DNS record table page ("+(listc+1)+")");
										listc*=10;
									do
									{
										a.sendMessage(listc+": "+res.getString("ip")+"    "+res.getString("name"));
										listc++;
									}
									while(res.next());
									}
								} catch (SQLException e) {
									if(n<2)
									{
										n++;
										con=mysql.getConnection();
										if(con==null)
											con=mysql.openConnection();
										return onCommand(a, b, c, d,n);
									}
									else
									{
										this.getLogger().info("SQL error code: "+e.getErrorCode());
										this.getLogger().info("SQL error msg: "+e.getMessage());
										this.getLogger().info("SQL error state: "+e.getSQLState());
										return false;
									}
								}
							}
							return true;
						}
						break;
					default:
						// HAXX, if not /dns lookup, then make [name] first parameter
						if (d[0].equalsIgnoreCase("lookup")) {
							if (d.length==1) return false;
							d[0] = d[1];
						}
						d[0] = Normalizer.normalize(d[0], Form.NFD).replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
						if(!safeName(d[0]))
						{
							a.sendMessage("[CartDNS] No hacking this time");
							return true;
						}
						if(!existEntryByName(getName(d,0,1)))
						{
							if(d[0].equals(""))
								{a.sendMessage("[CartDNS] No dns with this name/ip"); return true;}
							if(!existEntryByIP(safeIP(d[0])))
							{
								a.sendMessage("[CartDNS] No dns with this name/ip");
							}
							else
							{
								ResultSet ress = getEntryByIP(safeIP(d[0]));
								a.sendMessage("[CartDNS] Name: "+ress.getString("name")+" IP: "+ress.getString("ip"));
								a.sendMessage("[CartDNS] Author: "+ress.getString("username"));
							}
							return true;
						}
						else
						{
							ResultSet ress = getEntryByName(d[0]);
							a.sendMessage("[CartDNS] Name: "+ress.getString("name")+" IP: "+ress.getString("ip"));
							a.sendMessage("[CartDNS] Author: "+ress.getString("username"));
							return true;
						}
					}
				}
				catch (SQLException e) {
					if(n<2)
					{
						n++;
						con=mysql.getConnection();
						if(con==null)
							con=mysql.openConnection();
						return onCommand(a, b, c, d,n);
					}
					else
					{
						this.getLogger().info("SQL error code: "+e.getErrorCode());
						this.getLogger().info("SQL error msg: "+e.getMessage());
						this.getLogger().info("SQL error state: "+e.getSQLState());
						return false;
					}
				}
			}
		}
		else if(c.toLowerCase().equals("dest") && a.hasPermission("cartdns.user"))
		{
			if(!(a instanceof Player))
			{
				a.sendMessage("Only players can use /dest");
				return true;
			}
			if(d.length==0)
			{
				a.sendMessage("[DNS] Usage: /dest [name]");
				return true;
			}
			try {
				d[0] = Normalizer.normalize(d[0], Form.NFD).replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
				if(!safeName(getName(d,0,0)))
					{a.sendMessage("No hacking this time"); return true;}
				if(!existEntryByName(getName(d,0,0)))
				{
					a.sendMessage("[DNS] No IP for hostname "+getName(d,0,0));
					return true;
				}
				else
				{
					ResultSet res = getEntryByName(getName(d,0,0));
					((Player)a).performCommand("mego "+res.getString("ip"));
					return true;
				}
			} catch (SQLException e) {
				if(n<2)
				{
					n++;
					con=mysql.getConnection();
					if(con==null)
						con=mysql.openConnection();
					return onCommand(a, b, c, d,n);
				}
				else
				{
					this.getLogger().info("SQL error code: "+e.getErrorCode());
					this.getLogger().info("SQL error msg: "+e.getMessage());
					this.getLogger().info("SQL error state: "+e.getSQLState());
					return false;
				}
			}
		}
		else if(a instanceof Player)
		{
				Player bb=(Player)a;
		}
		return false;
	}

	@EventHandler
	public void onSignCreate(SignCreateEvent event) {
		if (event.getIc() instanceof Station) {
			try {
				Station station = (Station) event.getIc();
				String ip = station.getSignAddress().toString();
				String name = station.getStationName();
				Player player = event.getPlayer();
				if (! ip.equals("") && ! name.equals("")) {
					name = Normalizer.normalize(name, Form.NFD).replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
					if (safeName(name) && ! existEntryByName(name,player))
						createEntry(name, safeIP(ip), player.getUniqueId().toString(), player.getName());
				}
			} catch (SQLException e) {
				this.getLogger().info("SQL error code: "+e.getErrorCode());
				this.getLogger().info("SQL error msg: "+e.getMessage());
				this.getLogger().info("SQL error state: "+e.getSQLState());
			}
		}
	}

	@EventHandler
	public void onSignRemove(SignRemoveEvent event) {
		if (event.getIc() instanceof Station) {
			try {
				String name = ((Station) event.getIc()).getStationName();
				name = Normalizer.normalize(name, Form.NFD).replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
				if (safeName(name))
					removeEntry(name);
			} catch (SQLException e) {
				this.getLogger().info("SQL error code: "+e.getErrorCode());
				this.getLogger().info("SQL error msg: "+e.getMessage());
				this.getLogger().info("SQL error state: "+e.getSQLState());
			}
		}
	}

	@EventHandler
	public void onUpdaterSetStation(UpdaterSetStationEvent event) {
		try {
			String ip = event.getNewAddress().toString();
			String name = event.getName();
			if (! name.equals("")) {
				name = Normalizer.normalize(name, Form.NFD).replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
				if (safeName(name)) {
					removeEntry(name);
					createEntry(name, ip, "updater", "updater");
				}
			}
		} catch (SQLException e) {
			this.getLogger().info("SQL error code: "+e.getErrorCode());
			this.getLogger().info("SQL error msg: "+e.getMessage());
			this.getLogger().info("SQL error state: "+e.getSQLState());
		}
	}

	@EventHandler
	public void onUpdaterClearStation(UpdaterClearStationEvent event) {
		try {
			String name = event.getName();
			if (! name.equals("")) {
				name = Normalizer.normalize(name, Form.NFD).replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
				if (safeName(name)) {
					removeEntry(name);
				}
			}
		} catch (SQLException e) {
			this.getLogger().info("SQL error code: "+e.getErrorCode());
			this.getLogger().info("SQL error msg: "+e.getMessage());
			this.getLogger().info("SQL error state: "+e.getSQLState());
		}
	}

	@EventHandler
	public void onUpdaterPassStation(UpdaterPassStationEvent event) {
		try {
			String name = event.getName();
			if (! name.equals("")) {
				Address ip = event.getAddress();
				name = Normalizer.normalize(name, Form.NFD).replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
				getLogger().info("pass station name : " + name);
				if (ip.isValid() && safeName(name) && ! existEntryByName(name)) {
					createEntry(name, ip.toString(), "updater", "updater");
				}
			}
		} catch (SQLException e) {
			this.getLogger().info("SQL error code: "+e.getErrorCode());
			this.getLogger().info("SQL error msg: "+e.getMessage());
			this.getLogger().info("SQL error state: "+e.getSQLState());
		}
	}

	public String resolve(String name) {
		name = Normalizer.normalize(name, Form.NFD).replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
		try {
			if(safeName(name) && existEntryByName(name))
				{
					return getEntryByName(name).getString("ip");
				}
		} catch (SQLException e) {
			this.getLogger().info("SQL error code: "+e.getErrorCode());
			this.getLogger().info("SQL error msg: "+e.getMessage());
			this.getLogger().info("SQL error state: "+e.getSQLState());
		}
		return "";
	}

	private boolean removeEntry(String name) throws SQLException {
		if(existEntryByName(name))
		{
			s.executeUpdate("DELETE FROM `cart_dns` WHERE LOWER(`name`)='"+name.toLowerCase()+"'");
			return true;
		}
		return false;
	}


	private boolean existEntryByName(String name, CommandSender a) throws SQLException {
		if (existEntryByName(name)) {
			ResultSet res = getEntryByName(name);
			a.sendMessage("[CartDNS] DNS name "+res.getString("name")+" exist with ip "+res.getString("ip")+" .");
			return true;
		}
		return false;
	}

	private boolean existEntryByName(String name) throws SQLException {
		ResultSet res=s.executeQuery("SELECT * FROM `cart_dns` WHERE LOWER(`name`)='"+name.toLowerCase()+"'");
		return res.next();
	}

	private boolean existEntryByIP(String ip) throws SQLException {
		ResultSet res=s.executeQuery("SELECT * FROM `cart_dns` WHERE `ip`='"+ ip +"'");
		return res.next();
	}

	private ResultSet getEntryByName(String name) throws SQLException {
		return s.executeQuery("SELECT * FROM `cart_dns` WHERE LOWER(`name`)='"+name.toLowerCase()+"'");
	}

	private  ResultSet getEntryByIP(String ip) throws SQLException {
		return s.executeQuery("SELECT * FROM `cart_dns` WHERE `ip`='"+ip+"'");
	}

	private void createEntry(String name, String ip, String uu, String username) throws SQLException {
		String ds="INSERT INTO `cart_dns` (`ip`,`name`,`username`,`uuid`) VALUES('"+ip+"','" + name +"','"+username+"','"+uu+"')";
		s.executeUpdate(ds);
	}

	private String safeIP(String input)
	{
		char[] ch=input.toCharArray();
		String output="";
		int cnt=0,cnt2=0;
		for(char c:ch)
		{
			if(Character.isDigit(c))
			{
				if(cnt>=5)
					return "";
				else
					output+=c;
			}
			else if(c=='.')
			{
				if(cnt==0)
					return "";
				if(cnt2==2)
					return "";
				output+=c;
				cnt=0;
				cnt2++;
			}
			else
				return "";
			cnt++;
		}
		return output;
	}
	private boolean safeName(String input)
	{
		if(input.toLowerCase().contains(" or ") || input.toLowerCase().contains(" and ")
				|| input.toLowerCase().contains(" union "))
			return false;
		Pattern p = Pattern.compile("[^a-z0-9/*+$!:.%@_\\-#&\\s]", Pattern.CASE_INSENSITIVE);
		Matcher m = p.matcher(input);
		if(m.find())
			return false;
		return true;
	}
	private String host,user,database,port,password, sql;
	private String getName(String[] input, int s, int e)
	{
		String output=input[s];
		for(int i=s+1;i<input.length-e;i++)
			output+=" "+input[i];
		return output;
	}
	private void firstrun()
    {
    	File f = new File(this.getDataFolder() + "/");
    	if(!f.exists())
    	    f.mkdir();
    	f = new File(this.getDataFolder()  + "/config.yml");
    	if(!f.exists())
    	{
			try {
				f.createNewFile();
		        YamlConfiguration config = YamlConfiguration.loadConfiguration(f);
		        config.set("hostname", "localhost");
		        config.set("database", "minecraft");
		        config.set("port", "minecraft");
		        config.set("user", "root");
		        config.set("password", "root");
		        config.set("sql", "sqllite");
		        config.save(f);
			} catch (IOException e) {
				e.printStackTrace();
			}
    	}
        YamlConfiguration config = YamlConfiguration.loadConfiguration(f);
    	if(config.isSet("database"))
    		database=config.getString("database");
		sql = config.getString("sql", "mysql");
		if(sql.equalsIgnoreCase("mysql")) {
			if(config.isSet("hostname"))
				host=config.getString("hostname");
			if(config.isSet("port"))
				port=config.getString("port");
			if(config.isSet("user"))
				user=config.getString("user");
			if(config.isSet("password"))
				password=config.getString("password");
		}
    }
}
