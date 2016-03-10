# Introduction #

This page includes some tips not listed on main page. Some information could be related to trunk only as some functionality could be missing in current released versions.


# Details #

You may get the list of implemented checks by this command:
```
java -jar /usr/local/vmbix/vmbix.jar (or just "vmbix")
```

```
Available methods :
about
datastore.discovery
datastore.size[name,free]
datastore.size[name,total]
datastore.size[name,provisioned]
datastore.size[name,uncommitted]
esx.connection[name]
esx.cpu.load[name,cores]
esx.cpu.load[name,total]
esx.cpu.load[name,used]
esx.discovery
esx.maintenance[name]
esx.memory[name,total]
esx.memory[name,used]
esx.path[name,active]
esx.path[name,dead]
esx.path[name,disabled]
esx.path[name,standby]
esx.status[name]
esx.vms.memory[name,active]
esx.vms.memory[name,ballooned]
esx.vms.memory[name,compressed]
esx.vms.memory[name,consumed]
esx.vms.memory[name,overheadConsumed]
esx.vms.memory[name,private]
esx.vms.memory[name,shared]
esx.vms.memory[name,swapped]
esx.counter[name,counter,[instance,interval]]
event.latest[*]
counters[name]
ping
vm.consolidation[name,needed]
vm.cpu.load[name,cores]
vm.cpu.load[name,total]
vm.cpu.load[name,used]
vm.discovery[*]
vm.folder[name]
vm.guest.disk.all[name]
vm.guest.disk.capacity[name,disk]
vm.guest.disk.free[name,disk]
vm.guest.ip[name]
vm.guest.name[name]
vm.guest.os[name]
vm.guest.tools.mounted[name]
vm.guest.tools.running[name]
vm.guest.tools.version[name]
vm.host[name]
vm.memory[name,active]
vm.memory[name,ballooned]
vm.memory[name,compressed]
vm.memory[name,consumed]
vm.memory[name,overheadConsumed]
vm.memory[name,private]
vm.memory[name,shared]
vm.memory[name,swapped]
vm.memory[name,total]
vm.counter[name,counter,[instance,interval]]
vm.powerstate[name]
vm.status[name]
vm.storage.committed[name]
vm.storage.uncommitted[name]
vm.storage.unshared[name]

```

# How to implement your own checks #
  1. Find a function called
```
private void checkAllPatterns                (String string, PrintWriter out  )
```
  1. Add your own pattern. For example this string:
```
Pattern pHostCpuUsed            = Pattern.compile("^(?:\\s*ZBXD.)?.*esx\\.cpu\\.load\\[(.+),used\\]"             );        // :checks host cpu usage
```
will be responsible for this item:
```
esx.cpu.load[{HOST.DNS},used]
```
  1. Scroll down to the next block of code in the same function starting with "String found;", add you own "found=" block:
```
found = checkPattern(pHostCpuUsed           ,string); if (found != null) { getHostCpuUsed           (found, out); return; }
```
This one calls a function called "getHostCpuUsed" with {HOST.DNS} as an a first argument and a PrintWriter instance as a second one.
  1. Your function should accept String and PrintWriter arguments. It should return values like that:
```
out.print(value);
out.flush();
```

# Version history #

1.1.1
  * Added performance counters for ESX hosts.
  * Added new methods for datastores (danrog):
```
 datastore.size[name,provisioned]
 datastore.size[name,uncommitted]
```
  * vmbix now displays a version number when called without arguments.

1.1.0
  * Fixed the unnecessary carriage return in the output
  * Added Zabbix Low-Level Discovery methods to automatically create datastores, hosts, virtual machines, and VM disks. A JSON-formatted output is displayed (using Google GSON), ex:
```
 # zabbix_get -s localhost -p 12050 -k esx.discovery[*]
 {"data":[{"{#ESXHOST}":"esx0.domain.local"},{"{#ESXHOST}":"esx1.domain.local"}]}
```
  * Added several items:
    * VMWare Performance Manager counters:
```
  counters[name]: list the available counters for an entity (VM, host, datastore)
  vm.counter[name,counter,[instance,interval]]: displays the value of the counter with optional interval/instance
```
> > The method outputs an aggregated sum or average of real-time values, ex:
```
  # zabbix_get -s localhost -p 12050 -k vm.counter[VMNAME,cpu.ready,,200] 
491
```
> > An additional **interval** parameter was added to the configuration file to specify the default interval for the performance counter queries.
    * VM Tools status methods
    * ping : always returns 1, to check vmbix availability
    * about : display vCenter SDK version
    * guest IP/hostname methods
    * etc
  * The status/powerstate methods now return an integer value instead of a string (ex: "poweredOff"). This is better to store integers than strings in Zabbix and allows for graphing. Typically :
```
 Running State:
 0 -> poweredOff
 1 -> poweredOn
 2 -> suspended
 3 -> unknown 

 Status:
 0 -> grey
 1 -> green
 2 -> yellow
 3 -> red
 4 -> unknown
```
  * The Zabbix templates haven't been updated yet.

1.0.1
  * Fixed host used memory checks(returned cpu used MHz instead of memory used MB), fixed a custom multiplier for the same item.
  * Added several items:
    * Average private          memory usage in % for all powered on vms.
    * Average shared           memory usage in % for all powered on vms.
    * Average swapped          memory usage in % for all powered on vms.
    * Average compressed       memory usage in % for all powered on vms.
    * Average overheadConsumed memory usage in % for all powered on vms.
    * Average consumed         memory usage in % for all powered on vms.
    * Average balooned         memory usage in % for all powered on vms.
    * Average active           memory usage in % for all powered on vms.

1.0.0
  * First release