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
import org.bukkit.plugin.java.JavaPlugin;

import code.husky.mysql.MySQL;


public class CartDNS extends JavaPlugin{
	MySQL mysql;
	Connection con;
	Statement s;
	boolean err=false;
	@Override
	public void onLoad()
	{
		firstrun();
		mysql = new MySQL(this, host, port, database, user, password);
		con=mysql.openConnection();
		try {
			s = con.createStatement();
			ResultSet res=s.executeQuery("show tables like 'cart_dns'");
			if(res.next())
			{
				this.getLogger().info("Table cart_dns exist.");
			}
			else
			{
				this.getLogger().info("Table cart_dns dont exist. Creating new one.");
				if(!s.execute("create table cart_dns (ip varchar(11) not null primary key,username varchar(20) not null,uuid varchar(128) not null,name varchar(20) not null unique key)"))
				{
					this.getLogger().info("Table cart_dns cannot be created.");
					err=true; return;
				}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
	@Override
	public void onEnable()
	{
	}
	@Override
	public boolean onCommand(CommandSender a, Command b , String c, String[] d)
	{if(c.toLowerCase().equals("dns") && a.hasPermission("cartdns.manager"))
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
		else if(d.length>=2)
		{
		// Strip diacritics from Station name
		d[1] = Normalizer.normalize(d[1], Form.NFD).replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
			try{
			switch(d[0])
			{
			case "create":
				if(!safeName(d[1]))
				{
					a.sendMessage("[CartDNS] No hacking this time");
					return true;
				}
				else if(d.length<3 || safeIP(d[2])=="" || d[1].length()>20)
				{
					a.sendMessage("[CartDNS] Wrong IP/Name or not enought args.");
					return true;
				}
				ResultSet res=s.executeQuery("SELECT * FROM `cart_dns` WHERE LOWER(`name`)='"+getName(d,1,1).toLowerCase()+"'");
				
				if(!res.next())
				{
					res=s.executeQuery("SELECT * FROM `cart_dns` WHERE `ip`='"+safeIP(d[2])+"'");
					if(!res.next())
					{
						String uu="Console",user="Console";
						if((a instanceof Player))
						{
							uu=((Player)a).getUniqueId().toString();
							user=((Player)a).getName();
						}
						String ds="INSERT INTO `cart_dns` (`ip`,`name`,`username`,`uuid`) VALUES('"+safeIP(d[2])+"','"+getName(d,1,1)+"','"+user+"','"+uu+"')";
						s.executeUpdate(ds);
						a.sendMessage("[CartDNS] Added");
					}
					else
					{
						a.sendMessage("[CartDNS] DNS ip "+res.getString("ip")+" exist with name "+res.getString("name")+" .");
						return true;
					}
				}
				else
				{
					a.sendMessage("[CartDNS] DNS name "+res.getString("name")+" exist with ip "+res.getString("ip")+" .");
					return true;
				}
				break;
			case "lookup": 
			ResultSet ress=s.executeQuery("SELECT * FROM `cart_dns` WHERE LOWER(`name`)='"+getName(d,1,0).toLowerCase()+"'");
			if(!ress.next())
			{
				if(safeIP(d[1])=="")
					{a.sendMessage("[CartDNS] No dns with this name/ip"); return true;}
				ress=s.executeQuery("SELECT * FROM `cart_dns` WHERE `ip`='"+safeIP(d[1])+"'");
				if(!ress.next())
				{
					a.sendMessage("[CartDNS] No dns with this name/ip");
				}
				else
				{
					a.sendMessage("[CartDNS] Name: "+ress.getString("name")+"	IP: "+ress.getString("ip"));
					a.sendMessage("[CartDNS] Author: "+ress.getString("username"));
				}
				return true;
			}
			else
			{
				a.sendMessage("[CartDNS] Name: "+ress.getString("name")+"	IP: "+ress.getString("ip"));
				a.sendMessage("[CartDNS] Author: "+ress.getString("username"));
				return true;
			}
			case "remove":
				ResultSet resss=s.executeQuery("SELECT * FROM `cart_dns` WHERE LOWER(`name`)='"+getName(d,1,1).toLowerCase()+"'");
				
				if(resss.next())
				{
					s.executeUpdate("DELETE FROM `cart_dns` WHERE LOWER(`name`)='"+getName(d,1,1).toLowerCase()+"'");
					a.sendMessage("[CartDNS] Deleted");
					return true;
				}
				break;
			}
		}
		catch (SQLException e) {
			this.getLogger().info("SQL error code: "+e.getErrorCode());
			this.getLogger().info("SQL error msg: "+e.getMessage());
			this.getLogger().info("SQL error state: "+e.getSQLState());
		}
		
	}
	}
	if(c.toLowerCase().equals("dns") && (a.hasPermission("cartdns.manager")|| a.hasPermission("cartdns.user")))
	{
		if(d.length==0)
		{
			a.sendMessage("/dns list [page] - list all DNS records.");
		}
		else if(d.length>=1 && d[0]=="list")
		{
			int listc=1;
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
					a.sendMessage("DNS record table page ("+listc+")");
					listc*=10;
				do
				{
					a.sendMessage(listc+": "+res.getString("ip")+"    "+res.getString("name"));
					listc++;
				}
				while(res.next());
				}
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
		return true;
	}
	else if(c.toLowerCase().equals("dest") && (a.hasPermission("cartdns.user")|| a.hasPermission("cartdns.user")))
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
		ResultSet res=s.executeQuery("SELECT * FROM `cart_dns` WHERE LOWER(`name`)='"+getName(d,0,0).toLowerCase()+"'");
		if(!res.next())
		{
			a.sendMessage("[DNS] No IP for hostname "+getName(d,0,0));
			return true;
		}
		else
		{
				((Player)a).performCommand("mego "+res.getString("ip"));
				return true;
		}
		} catch (SQLException e) {
			this.getLogger().info("SQL error code: "+e.getErrorCode());
			this.getLogger().info("SQL error msg: "+e.getMessage());
			this.getLogger().info("SQL error state: "+e.getSQLState());
		}
		
	}
	else if(c.toLowerCase().equals("showmap") && (a.hasPermission("cartdns.showme")))
	{
		if(a instanceof Player)
		{
			Player bb=(Player)a;
			bb.chat("http://map.majncraft.cz/?worldname="+bb.getWorld().getName()+"&zoom=8&x="+bb.getLocation().getBlockX()+"&y=64&z="+bb.getLocation().getBlockZ());
		}
	}
	return false;
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
		if(input.toLowerCase().contains(" OR ") || input.toLowerCase().contains(" AND "))
			return false;
		Pattern p = Pattern.compile("[^a-z0-9\\s]", Pattern.CASE_INSENSITIVE);
		Matcher m = p.matcher(input);
		if(m.find())
			return false;
		return true;
	}
	private String host,user,database,port,password;
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
		        config.save(f);
			} catch (IOException e) {
				e.printStackTrace();
			}
    	}
        YamlConfiguration config = YamlConfiguration.loadConfiguration(f);
    	if(config.isSet("hostname"))
    		host=config.getString("hostname");
    	if(config.isSet("port"))
    		port=config.getString("port");
    	if(config.isSet("database"))
    		database=config.getString("database");
    	if(config.isSet("user"))
    		user=config.getString("user");
    	if(config.isSet("password"))
    		password=config.getString("password");
    	
    }
}
