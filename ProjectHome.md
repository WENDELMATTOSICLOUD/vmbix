# VmBix #
# This page is deprecated, please find the up to date version on [github](https://github.com/dav3860/vmbix) #
## What is VmBix? ##
VmBix is a multi-thread TCP server written in java, it accepts connection from zabbix server or zabbix get (supports some custom zabbix checks) and translates them to vmware api calls.
## Why VmBix? ##
Why not perl sdk? Because VMWare perl SDK is not thread safe. I've written the same functionality in perl first and faced huge gapes in collecting data on a "some kind of heavy load", about 500 checks/minute. I've tested VmBix with about 1800 checks per minute, it used 4-5 threads to aggregate all the connections and about 15%-20% of some modern CPU core. Threads are created and killed dynamically when needed. Number of threads depends on zabbix check frequency and vCenter/ESX(i) response time.
## Supported OS ##
VmBix jar itself should run on any os with [jre](http://www.oracle.com/technetwork/java/javase/downloads/index.html) installed.
It's tested on:
  * Windows XP SP3
  * Mac OS 10.7
  * RHEL 6
However, this product contains a number of shell scripts that should run on any `*`nix OS and init scripts designed for rpm-based linux and tested on RHEL 6.

# Quick start #
## Build from source ##
Note: you'll need to intall jdk and ant to follow this article. Sources could also be compiled manually, without ant.

**Download source code:
```
svn checkout http://vmbix.googlecode.com/svn/trunk/ vmbix-read-only
```** Compile:
```
cd vmbix-read-only
ant
```

Ant should download required jargs and vijava libraries, compile the sources and place compiled code to VmBix-$version folder.

## Download precompiled zip archive ##
  1. Download the latest version from [downloads](http://code.google.com/p/vmbix/downloads/list) page in zip format.
  1. unzip. You should get a VmBix-$version folder.

## Installation ##
### Copy files ###
Your VmBix-$version folder should look like this:
```
find VmBix-beta-0.0.1/ -type f
VmBix-beta-0.0.1/rmp-based/etc/init.d/vmbixd          # init script, will start vmbixd as daemon
VmBix-beta-0.0.1/rmp-based/etc/vmbix/vmbix.conf       # config file
VmBix-beta-0.0.1/rmp-based/usr/local/sbin/vmbix       # sbin script to run the tool
VmBix-beta-0.0.1/rmp-based/usr/local/sbin/vmbixd      # like vmbix but will start in background
VmBix-beta-0.0.1/rmp-based/usr/local/vmbix/vmbix.jar  # jar file itself
VmBix-beta-0.0.1/zabbix_templates/Datastore.xml       # Datastore zabbix template
VmBix-beta-0.0.1/zabbix_templates/Host.xml            # Host zabbix template 
VmBix-beta-0.0.1/zabbix_templates/VM.xml              # Virtual Machine zabbix template
```

Copy files from VmBix-$version/rmp-based/ folder to you system accordingly to there paths. Make shell scripts executable:
```
chmod +x /etc/init.d/vmbixd
chmod +x /usr/local/sbin/vmbix
chmod +x /usr/local/sbin/vmbixd
```

### Check that VmBix runs on your system ###
Note: To run VmBix you'll have to install jre, OpenJDK should suite but not tested. All the shell scripts are tested on rhel 6 but thould work on other **nix distributions as well. Init script (etc/init.d/vmbixd) might need some modifications to work on debian based or**bsd systems.

Try to run /usr/local/sbin/vmbix, you should get a "usage" output.
```
# /usr/local/sbin/vmbix
Usage:
vmbix {-P|--port} listenPort {-s|--serviceurl} http[s]://serveraddr/sdk {-u|--username} username {-p|--password} password [-f|--pid pidfile]
or
vmbix [-c|--config] config_file  [-f|--pid pidfile]
```

Note: if some option is presented both in command line and in config file, command line one will be used. If any of required options is not presented at all you'll see the usage output with blinking missing option.

User name, password and vsphere service url are the required option.

### Configure daemon ###
It is strongly recommended to check you parameters before writing them down to a config file. Run vmbix with you username, password and service url:
```
$ vmbix -P 12050 -u username -p password -s https://vcenter.mycompany.com/sdk
starting server on port
port opened
time taken:5450 #this line means that you got connected to vcenter/esx
```

If you find your output similar to one listed here, feel free to set parameters at /etc/vmbix/vmbix.conf.

To install as daemon:
```
chkconfig --add vmbixd
```

Now you may start the daemon:
```
service vmbixd start
```

And configure autostart if you wish:
```
chkconfig vmbixd on
```

For logs, check:
```
tail -f /var/log/messages|grep vmbix
```

### Configure host in zabbix UI ###
  1. Import any template from zabbix\_templates.
  1. Create a host based on imported template. There are at least two ways of configuring host connection:
    1. Set host ip to 127.0.0.1 or to the ip of the server where VmBix runs. Set "Connect to" to "IP address". Set port to 12050 or the one you've set in vmbix config file.
    1. Set port to 12050 or the one you've set in vmbix config file. Use iptables rule to redirect all outgoing connections to port 12050 to localhost (assumes you run vmbix and zabbix server on the same server):
```
iptables -A OUTPUT -t nat -p tcp --dport 12050 -j DNAT --to 127.0.0.1:12050
```
> > Edit ports and "--to" parameter if needed. Ensure that iptables service is started.


## Supported zabbix checks ##
```
status[hostname]
esx.cpu.load[hostname,used]
esx.cpu.load[hostname,total]
esx.cpu.load[hostname,cores]
esx.memory[hostname,used]
esx.memory[hostname,total]
esx.vms.memory[name,private]           :this is a heavy check. Counts average private          memory usage in % for all powered on vms.
esx.vms.memory[name,shared]            :this is a heavy check. Counts average shared           memory usage in % for all powered on vms.
esx.vms.memory[name,swapped]           :this is a heavy check. Counts average swapped          memory usage in % for all powered on vms.
esx.vms.memory[name,compressed]        :this is a heavy check. Counts average compressed       memory usage in % for all powered on vms.
esx.vms.memory[name,overheadConsumed]  :this is a heavy check. Counts average overheadConsumed memory usage in % for all powered on vms.
esx.vms.memory[name,consumed]          :this is a heavy check. Counts average consumed         memory usage in % for all powered on vms.
esx.vms.memory[name,balooned]          :this is a heavy check. Counts average balooned         memory usage in % for all powered on vms.
esx.vms.memory[name,active]            :this is a heavy check. Counts average active           memory usage in % for all powered on vms.           
vm.cpu.load[vmname,used]
vm.cpu.load[vmname,total]
vm.memory[vmname,private]                
vm.memory[vmname,shared]                 
vm.memory[vmname,swapped]                
vm.memory[vmname,compressed]             
vm.memory[vmname,overheadConsumed]       
vm.memory[vmname,consumed]               
vm.memory[vmname,balooned]               
vm.memory[vmname,active]                 
vm.memory[vmname,total]
datastore.size[datastorename,free]
datastore.size[datastorename,total]
```

You can check any of these like this:
```
zabbix_get -s localhost -p 12050 -k esx.cpu.load[myesxhost,used]
```


[![](https://www.paypalobjects.com/en_US/i/btn/btn_donate_LG.gif)](https://www.paypal.com/cgi-bin/webscr?cmd=_donations&business=Z6NFKB23EGGLJ&lc=RU&item_name=VmBix&currency_code=USD&bn=PP%2dDonationsBF%3abtn_donate_LG%2egif%3aNonHosted)