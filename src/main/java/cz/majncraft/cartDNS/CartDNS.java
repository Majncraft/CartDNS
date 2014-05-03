package cz.majncraft.cartDNS;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
				if(!s.execute("create table cart_dns (ip varchar(11) not null primary key,name varchar(20) not null unique key)"))
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
			try{
			switch(d[0])
			{
			case "create":
				if(!safeName(d[1]))
				{
					a.sendMessage("[CartDNS]No hacking this time");
					return true;
				}
				else if(d.length<3 || safeIP(d[2])=="" || d[1].length()>20)
				{
					a.sendMessage("[CartDNS]Wrong IP/Name or not enought args.");
					return true;
				}
				ResultSet res=s.executeQuery("SELECT * FROM `cart_dns` WHERE LOWER(`name`)='"+d[1].toLowerCase()+"'");
				
				if(!res.next())
				{
					res=s.executeQuery("SELECT * FROM `cart_dns` WHERE `ip`='"+safeIP(d[2])+"'");
					if(!res.next())
					{
						String ds="INSERT INTO `cart_dns` (`ip`,`name`) VALUES('"+safeIP(d[2])+"','"+d[1]+"')";
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
			ResultSet ress=s.executeQuery("SELECT * FROM `cart_dns` WHERE LOWER(`name`)='"+d[1].toLowerCase()+"'");
			if(!ress.next())
			{
				if(safeIP(d[1])=="")
					{a.sendMessage("[CartDNS]No dns with this name/ip"); return true;}
				ress=s.executeQuery("SELECT * FROM `cart_dns` WHERE `ip`='"+safeIP(d[1])+"'");
				if(!ress.next())
				{
					a.sendMessage("[CartDNS]No dns with this name/ip");
				}
				else
				{
					a.sendMessage("[CartDNS]Name: "+ress.getString("name")+"		IP: "+ress.getString("ip"));
				}
				return true;
			}
			else
			{
				a.sendMessage("[CartDNS]Name: "+ress.getString("name")+"		IP: "+ress.getString("ip"));
				return true;
			}
			case "remove":
				ResultSet resss=s.executeQuery("SELECT * FROM `cart_dns` WHERE LOWER(`name`)='"+d[1].toLowerCase()+"'");
				
				if(resss.next())
				{
					s.executeUpdate("DELETE FROM `cart_dns` WHERE LOWER(`name`)='"+d[1].toLowerCase()+"'");
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
	if(c.toLowerCase().equals("dns") && (a.hasPermission("cartdns.user")|| a.hasPermission("cartdns.user")))
	{
		if(d.length==0)
		{
			a.sendMessage("/dns list [page] - list all DNS records.");
		}
		else if(d.length>=1 && d[0]=="list")
		{
			int listc=0;
			if(d[1].matches("-?\\d+"))
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
					a.sendMessage(listc+": "+res.getString("ip")+"		"+res.getString("name"));
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
		try {
		if(!safeName(d[0]))
			{a.sendMessage("No hacking this time"); return true;}
		ResultSet res=s.executeQuery("SELECT * FROM `cart_dns` WHERE LOWER(`name`)='"+d[0].toLowerCase()+"'");
		if(!res.next())
		{
			a.sendMessage("[DNS] No IP for hostname "+d[0]);
			return true;
		}
		else
		{
				((Player)a).performCommand("/sendto "+res.getString("name"));
			return true;
		}
		} catch (SQLException e) {
			this.getLogger().info("SQL error code: "+e.getErrorCode());
			this.getLogger().info("SQL error msg: "+e.getMessage());
			this.getLogger().info("SQL error state: "+e.getSQLState());
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
				if(cnt>=3)
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
		Pattern p = Pattern.compile("[^a-z0-9 ]", Pattern.CASE_INSENSITIVE);
		Matcher m = p.matcher(input);
		if(m.find())
			return false;
		return true;
	}
	private String host,user,database,port,password;
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
