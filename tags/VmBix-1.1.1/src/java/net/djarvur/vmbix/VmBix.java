/*
# vmware api comunication daemon
# This daemon connects to vCenter with vijava SDK
# and gives access to some statistics over tcp/ip socket
#
# Written by Roman Belyakovsky
#            ihryamzik@gmail.com
# 
# Release 1.1.0 by dav3860
#                  dav3860chom@yahoo.fr
*/

package net.djarvur.vmbix;

import java.net.*;
import java.rmi.*;
import java.io.*;
import java.util.*;
import java.util.regex.*;
import java.net.URL;
import com.vmware.vim25.*;
import com.vmware.vim25.mo.*;
import com.vmware.vim25.mo.util.*;
import jargs.gnu.CmdLineParser;
import java.lang.management.*;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;

public class VmBix {
    static ArrayList<Socket> sockets;
    static ServiceInstance serviceInstance;
    static InventoryNavigator inventoryNavigator;
    static String  sdkUrl;
    static String  uname;
    static String  passwd;
    static Integer port;
    static String  pidFile;
    static Integer interval;


    public static void main(String[] args) {
        try {
            sockets = new ArrayList<Socket> ();
            String pid = ManagementFactory.getRuntimeMXBean().getName().split("@")[0];

            CmdLineParser parser = new CmdLineParser();
            CmdLineParser.Option oUname  = parser.addStringOption( 'u', "username");
            CmdLineParser.Option oPasswd = parser.addStringOption( 'p', "password");
            CmdLineParser.Option oSurl   = parser.addStringOption( 's', "serviceurl");
            CmdLineParser.Option oPort   = parser.addIntegerOption('P', "port");
            CmdLineParser.Option oPid    = parser.addStringOption( 'f', "pid");
            CmdLineParser.Option oInterval = parser.addStringOption( 'i', "interval"); // Default interval for performance manager
            CmdLineParser.Option oConfig = parser.addStringOption( 'c', "config");
            
            try {
                parser.parse(args);
            }
            catch ( CmdLineParser.OptionException e ) {
                System.err.println(e.getMessage());
                usage("");
                System.exit(2);
            }
          
            sdkUrl  = (String )parser.getOptionValue(oSurl  );
            uname   = (String )parser.getOptionValue(oUname );
            passwd  = (String )parser.getOptionValue(oPasswd);
            port    = (Integer)parser.getOptionValue(oPort  );
            pidFile = (String )parser.getOptionValue(oPid   );
            interval = (Integer )parser.getOptionValue(oInterval   );
            
            String config = (String )parser.getOptionValue(oConfig);
            if (config != null) {
                Properties prop = new Properties();
                try {
                    InputStream is = new FileInputStream(config);
                    prop.load(is);
                    
                    if ( uname   == null ) uname   =                  prop.getProperty("username"   );
                    if ( passwd  == null ) passwd  =                  prop.getProperty("password"   );
                    if ( sdkUrl  == null ) sdkUrl  =                  prop.getProperty("serviceurl" );
                    if ( port    == null ) port    = Integer.parseInt(prop.getProperty("listenport"));
                    if ( pidFile == null ) pidFile =                  prop.getProperty("pidfile"    );
                    if ( interval == null ) interval = Integer.parseInt(prop.getProperty("interval"));
                }
                catch (IOException e) {
                    System.out.println(e.toString());
                }
            }
            
            if (sdkUrl == null || uname == null || passwd == null || port == null) { usage(""); }
            
            if (pidFile != null && pid != null) {
                createPid(pidFile,pid);
            }
            
            Shutdown sh = new Shutdown();
            Runtime.getRuntime().addShutdownHook(sh);

            while (true){
                try {
                    server ();
                }
                catch (java.rmi.RemoteException e) {
                    System.out.println("RemoteException: " +  e.toString());
                }
                VmBix.sleep(1000);
            }
            
        }

        catch (java.net.UnknownHostException e) {
            usage (e.toString());
        }
        catch (NumberFormatException e) {
            usage (e.toString());
        }
        catch (IOException e) {
            usage (e.toString());
        }

    };
    
    static void createPid(String pidFile, String pid) {
        try{
            // Create pid file 
            FileWriter fstream = new FileWriter(pidFile);
            BufferedWriter out = new BufferedWriter(fstream);
            out.write(pid + "\n");
            System.out.println("creating pid file " + pidFile + " " + pid);
            //Close the output stream
            out.close();
        }
        catch (Exception e) {
            //Catch exception if any
            System.err.println("Error: " + e.getMessage());
        }
    }
    
    private static void deletePid(){
        if (pidFile != null) {
            File f1 = new File(pidFile);
            boolean success = f1.delete();
            if (!success){
                System.out.println("Pid file deletion failed.");
                System.exit(0);
            }else{
                System.out.println("Pid file deleted.");
            }
        }
    }
    

    static void usage(String str) {
        String sname = "{-u|--username} \u001B[4musername\u001B[0m";
        String spass = "{-p|--password} \u001B[4mpassword\u001B[0m";
        String ssurl = "{-s|--serviceurl} \u001B[4mhttp[s]://serveraddr/sdk\u001B[0m";
        String sport = "{-P|--port} \u001B[4mlistenPort\u001B[0m";
        
        if (uname  == null) {sname = "\u001B[5m"+ sname + "\u001B[0m";}
        if (passwd == null) {spass = "\u001B[5m"+ spass + "\u001B[0m";}
        if (sdkUrl == null) {ssurl = "\u001B[5m"+ ssurl + "\u001B[0m";}
        if (port   == null) {sport = "\u001B[5m"+ sport + "\u001B[0m";}
        
        System.err.print(
            "VmBix version 1.1.1\n"
            + "Usage:\nvmbix "
            + sport + " " + ssurl + " " + sname + " " + spass + " [-f|--pid pidfile] [-i|--interval interval]" + "\n"
            + "or\nvmbix [-c|--config] config_file  [-f|--pid pidfile] [-i|--interval interval]\n\n"
            + "Available methods :                                  \n"
            + "about                                                \n"                                                                                    
            + "datastore.discovery                                  \n"                                                                                    
            + "datastore.size[name,free]                            \n"                                                                                    
            + "datastore.size[name,total]                           \n"
            + "datastore.size[name,provisioned]                     \n"                                                                                    
            + "datastore.size[name,uncommitted]                     \n"               
            + "esx.connection[name]                                 \n"                                                                                    
            + "esx.cpu.load[name,cores]                             \n"                                                                                    
            + "esx.cpu.load[name,total]                             \n"                                                                                    
            + "esx.cpu.load[name,used]                              \n"                                                                                    
            + "esx.discovery                                        \n"                                                                                    
            + "esx.maintenance[name]                                \n"                                                                                    
            + "esx.memory[name,total]                               \n"                                                                                    
            + "esx.memory[name,used]                                \n"                                                                                    
            + "esx.path[name,active]                                \n"                                                                                    
            + "esx.path[name,dead]                                  \n"                                                                                    
            + "esx.path[name,disabled]                              \n"                                                                                    
            + "esx.path[name,standby]                               \n"                                                                                    
            + "esx.status[name]                                     \n"                                                                                    
            + "esx.vms.memory[name,active]                          \n"
            + "esx.vms.memory[name,ballooned]                       \n"
            + "esx.vms.memory[name,compressed]                      \n"
            + "esx.vms.memory[name,consumed]                        \n"
            + "esx.vms.memory[name,overheadConsumed]                \n"
            + "esx.vms.memory[name,private]                         \n"
            + "esx.vms.memory[name,shared]                          \n"
            + "esx.vms.memory[name,swapped]                         \n"
            + "esx.counter[name,counter,[instance,interval]]        \n"                                                                                                
            + "event.latest[*]                                      \n"                                                                                    
            + "counters[name]                                       \n"                                                                                    
            + "ping                                                 \n"                                                                                    
            + "vm.consolidation[name,needed]                        \n"                                                                                    
            + "vm.cpu.load[name,cores]                              \n"                                                                                    
            + "vm.cpu.load[name,total]                              \n"                                                                                    
            + "vm.cpu.load[name,used]                               \n"                                                                                    
            + "vm.discovery[*]                                      \n"                                                                                    
            + "vm.folder[name]                                      \n"                                                                                    
            + "vm.guest.disk.all[name]                              \n"                                                                                    
            + "vm.guest.disk.capacity[name,disk]                    \n"                                                                                    
            + "vm.guest.disk.free[name,disk]                        \n"                                                                                    
            + "vm.guest.ip[name]                                    \n"                                                                                    
            + "vm.guest.name[name]                                  \n"                                                                                    
            + "vm.guest.os[name]                                    \n"                                                                                    
            + "vm.guest.tools.mounted[name]                         \n"                                                                                    
            + "vm.guest.tools.running[name]                         \n"                                                                                    
            + "vm.guest.tools.version[name]                         \n"                                                                                    
            + "vm.host[name]                                        \n"                                                                                    
            + "vm.memory[name,active]                               \n"                                                                                    
            + "vm.memory[name,ballooned]                            \n"                                                                                    
            + "vm.memory[name,compressed]                           \n"                                                                                    
            + "vm.memory[name,consumed]                             \n"                                                                                    
            + "vm.memory[name,overheadConsumed]                     \n"                                                                                    
            + "vm.memory[name,private]                              \n"                                                                                    
            + "vm.memory[name,shared]                               \n"                                                                                    
            + "vm.memory[name,swapped]                              \n"                                                                                    
            + "vm.memory[name,total]                                \n"                                                                                    
            + "vm.counter[name,counter,[instance,interval]]         \n"                                                                                    
            + "vm.powerstate[name]                                  \n"                                                                                    
            + "vm.status[name]                                      \n"                                                                                    
            + "vm.storage.committed[name]                           \n"                                                                                    
            + "vm.storage.uncommitted[name]                         \n"                                                                                    
            + "vm.storage.unshared[name]                            \n"            
            + ( str != null ? str + "\n" : "" )
            );
        System.exit(1);
    };

    
    public static synchronized void putConnection(Socket socket) throws IOException{
        if (sockets.size() < 150){
            sockets.add(socket);
            if (sockets.size() > ( Thread.activeCount() - 2) ){
                Request request = new Request (serviceInstance, null, inventoryNavigator);
                Connection thread = new Connection (request);
                thread.start();
            }
        }
        else {
            socket.close();
        }
    }
    
    public static synchronized Request pullConnection(){
        if (sockets.isEmpty()){
            return null;
        } else {
            
            Request request = new Request (serviceInstance, sockets.remove(0), null);
            return request;
        }
    }
    
    public static synchronized void updateConnection() throws IOException{
        long start = System.currentTimeMillis();
        serviceInstance = new ServiceInstance(new URL(sdkUrl), uname, passwd, true);
        if (serviceInstance == null) {
            System.out.println("serviceInstance in null! Connection failed.");
            return;
        }
        Folder rootFolder = serviceInstance.getRootFolder();
        inventoryNavigator = new InventoryNavigator(serviceInstance.getRootFolder());
        long end = System.currentTimeMillis();
        System.out.println("Connected to " + sdkUrl + ", time taken:" + (end-start) + "ms");
    }
    
    public static synchronized void shutdown() {
        try {
            deletePid();
        }
        catch (Exception e) {
            //Catch exception if any
            System.err.println("Error deleting pid: " + e.getMessage());
        }
        try {
            serviceInstance.getServerConnection().logout();
            System.out.println("disconnected");
        }
        catch (Exception e) {
            //Catch exception if any
            System.err.println("Error disconnecting: " + e.getMessage());
        }
        System.out.println("Shutted down");
    }
    
    public static synchronized Request updateConnectionSafe() {
        try {
            updateConnection();
        }
        catch (IOException e){
            System.out.println("Connection update error: " + e.toString() );
        }
        return new Request (serviceInstance, null, inventoryNavigator); 
    }
    
    static void sleep(int delay) {
        try {
            Thread.sleep(delay);
        }
        catch (InterruptedException e){
            System.out.println("thread sleep error: " + e.toString() );
        }
    }
    
    static void server() throws IOException {
        // long start = System.currentTimeMillis();
        updateConnection();
        System.out.println("starting server on port");
        ServerSocket listen = new ServerSocket (port);//(port, backlog, bindaddr)
        System.out.println("port opened, server started");
        // long end = System.currentTimeMillis();
        // System.out.println("Connected to " + sdkUrl + ", time taken:" + (end-start) + "ms");
        while (true) {
            Socket connected = listen.accept();
            // System.out.println("got connection from " 
                // + connected.getInetAddress().getHostAddress() 
                // + ":"
                // + connected.getPort()
                // + "\n"
                // );
            putConnection(connected);
        }
    };
    
    static class Request {
        public Socket          socket;
        public ServiceInstance serviceInstance;
        public InventoryNavigator inventoryNavigator;
        Request(ServiceInstance si, Socket socket, InventoryNavigator iv) {
            this.socket         = socket;
            this.serviceInstance = si;
            this.inventoryNavigator = iv;
        }
    }
    
    static class Connection extends Thread {
        Socket connected;
        ServiceInstance serviceInstance;
        InventoryNavigator inventoryNavigator;
        Connection(Request request) {
            // this.connected = connected;
            this.serviceInstance    = request.serviceInstance;
            this.inventoryNavigator = request.inventoryNavigator;
        }
        static private String checkPattern           (Pattern pattern, String string  )                    {
            Matcher matcher = pattern.matcher(string);
            if (matcher.find()){
                return matcher.group(1);
            }
            return null;
        }

        static private String[] checkMultiplePattern           (Pattern pattern, String string  )                    {
            Matcher matcher = pattern.matcher(string);
            if (matcher.find()){
                ArrayList<String> list = new ArrayList<String>();
                for(int m=1; m<matcher.groupCount()+1; m++) {
                    list.add(matcher.group(m));
                }
                String[] groups = list.toArray(new String[list.size()]);
                return groups;
            }
            return null;
        }        

        private void checkAllPatterns                (String string, PrintWriter out  ) throws IOException {
            Pattern pPing                   = Pattern.compile("^(?:\\s*ZBXD.)?.*(ping)"                                      );        //        
            Pattern pAbout                  = Pattern.compile("^(?:\\s*ZBXD.)?.*(about)"                                     );        //
            Pattern pLatestEvent            = Pattern.compile("^(?:\\s*ZBXD.)?.*(event\\.latest)"                            );        //            
            Pattern pVMs                    = Pattern.compile("^(?:\\s*ZBXD.)?.*vm\\.(discovery)"                            );        //
            Pattern pHosts                  = Pattern.compile("^(?:\\s*ZBXD.)?.*esx\\.(discovery)"                           );        // 
            Pattern pDatastores             = Pattern.compile("^(?:\\s*ZBXD.)?.*datastore\\.(discovery)"                     );        //             
            Pattern pHostConnection         = Pattern.compile("^(?:\\s*ZBXD.)?.*esx\\.connection\\[(.+)\\]"                  );        // 
            Pattern pHostStatus             = Pattern.compile("^(?:\\s*ZBXD.)?.*esx\\.status\\[(.+)\\]"                      );        // 
            Pattern pVmStatus               = Pattern.compile("^(?:\\s*ZBXD.)?.*vm\\.status\\[(.+)\\]"                       );        // 
            Pattern pHostMaintenance        = Pattern.compile("^(?:\\s*ZBXD.)?.*esx\\.maintenance\\[(.+)\\]"                 );        // 
            Pattern pHostCpuUsed            = Pattern.compile("^(?:\\s*ZBXD.)?.*esx\\.cpu\\.load\\[(.+),used\\]"             );        // 
            Pattern pHostDisabledPaths      = Pattern.compile("^(?:\\s*ZBXD.)?.*esx\\.path\\[(.+),disabled\\]"               );        // 
            Pattern pHostActivePaths        = Pattern.compile("^(?:\\s*ZBXD.)?.*esx\\.path\\[(.+),active\\]"                 );        // 
            Pattern pHostStandbyPaths       = Pattern.compile("^(?:\\s*ZBXD.)?.*esx\\.path\\[(.+),standby\\]"                );        // 
            Pattern pHostDeadPaths          = Pattern.compile("^(?:\\s*ZBXD.)?.*esx\\.path\\[(.+),dead\\]"                   );        // 
            Pattern pHostCpuTotal           = Pattern.compile("^(?:\\s*ZBXD.)?.*esx\\.cpu\\.load\\[(.+),total\\]"            );        // 
            Pattern pHostCpuCores           = Pattern.compile("^(?:\\s*ZBXD.)?.*esx\\.cpu\\.load\\[(.+),cores\\]"            );        // 
            Pattern pHostMemUsed            = Pattern.compile("^(?:\\s*ZBXD.)?.*esx\\.memory\\[(.+),used\\]"                 );        // 
            Pattern pHostMemTotal           = Pattern.compile("^(?:\\s*ZBXD.)?.*esx\\.memory\\[(.+),total\\]"                );        // 
            Pattern pHostMemStatsPrivate    = Pattern.compile("^(?:\\s*ZBXD.)?.*esx\\.vms.memory\\[(.+),private\\]"          );        // :this is a heavy check. Counts average private          memory usage in % for all powered on vms.
            Pattern pHostMemStatsShared     = Pattern.compile("^(?:\\s*ZBXD.)?.*esx\\.vms.memory\\[(.+),shared\\]"           );        // :this is a heavy check. Counts average shared           memory usage in % for all powered on vms.
            Pattern pHostMemStatsSwapped    = Pattern.compile("^(?:\\s*ZBXD.)?.*esx\\.vms.memory\\[(.+),swapped\\]"          );        // :this is a heavy check. Counts average swapped          memory usage in % for all powered on vms.
            Pattern pHostMemStatsCompressed = Pattern.compile("^(?:\\s*ZBXD.)?.*esx\\.vms.memory\\[(.+),compressed\\]"       );        // :this is a heavy check. Counts average compressed       memory usage in % for all powered on vms.
            Pattern pHostMemStatsOverhCons  = Pattern.compile("^(?:\\s*ZBXD.)?.*esx\\.vms.memory\\[(.+),overheadConsumed\\]" );        // :this is a heavy check. Counts average overheadConsumed memory usage in % for all powered on vms.
            Pattern pHostMemStatsConsumed   = Pattern.compile("^(?:\\s*ZBXD.)?.*esx\\.vms.memory\\[(.+),consumed\\]"         );        // :this is a heavy check. Counts average consumed         memory usage in % for all powered on vms.
            Pattern pHostMemStatsBallooned  = Pattern.compile("^(?:\\s*ZBXD.)?.*esx\\.vms.memory\\[(.+),ballooned\\]"        );        // :this is a heavy check. Counts average ballooned         memory usage in % for all powered on vms.
            Pattern pHostMemStatsActive     = Pattern.compile("^(?:\\s*ZBXD.)?.*esx\\.vms.memory\\[(.+),active\\]"           );        // :this is a heavy check. Counts average active           memory usage in % for all powered on vms.           
            Pattern pHostPerfCounterValue   = Pattern.compile("^(?:\\s*ZBXD.)?.*esx\\.counter\\[([^,]+),([^,]+)(?:,([^,]*))?(?:,([^,]*))?\\]" );                    
            Pattern pVmCpuUsed              = Pattern.compile("^(?:\\s*ZBXD.)?.*vm\\.cpu\\.load\\[(.+),used\\]"              );        // 
            Pattern pVmCpuTotal             = Pattern.compile("^(?:\\s*ZBXD.)?.*vm\\.cpu\\.load\\[(.+),total\\]"             );        // 
            Pattern pVmCpuCores             = Pattern.compile("^(?:\\s*ZBXD.)?.*vm\\.cpu\\.load\\[(.+),cores\\]"             );        // 
            Pattern pVmMemPrivate           = Pattern.compile("^(?:\\s*ZBXD.)?.*vm\\.memory\\[(.+),private\\]"               );        // 
            Pattern pVmMemShared            = Pattern.compile("^(?:\\s*ZBXD.)?.*vm\\.memory\\[(.+),shared\\]"                );        // 
            Pattern pVmMemSwapped           = Pattern.compile("^(?:\\s*ZBXD.)?.*vm\\.memory\\[(.+),swapped\\]"               );        // 
            Pattern pVmMemCompressed        = Pattern.compile("^(?:\\s*ZBXD.)?.*vm\\.memory\\[(.+),compressed\\]"            );        // 
            Pattern pVmMemOverheadConsumed  = Pattern.compile("^(?:\\s*ZBXD.)?.*vm\\.memory\\[(.+),overheadConsumed\\]"      );        // 
            Pattern pVmMemConsumed          = Pattern.compile("^(?:\\s*ZBXD.)?.*vm\\.memory\\[(.+),consumed\\]"              );        // 
            Pattern pVmMemBallooned         = Pattern.compile("^(?:\\s*ZBXD.)?.*vm\\.memory\\[(.+),ballooned\\]"             );        // 
            Pattern pVmMemActive            = Pattern.compile("^(?:\\s*ZBXD.)?.*vm\\.memory\\[(.+),active\\]"                );        // 
            Pattern pVmMemSize              = Pattern.compile("^(?:\\s*ZBXD.)?.*vm\\.memory\\[(.+),total\\]"                 );        // 
            Pattern pVmHost                 = Pattern.compile("^(?:\\s*ZBXD.)?.*vm\\.host\\[(.+)\\]"                         );        // 
            Pattern pVmPowerState           = Pattern.compile("^(?:\\s*ZBXD.)?.*vm\\.powerstate\\[(.+)\\]"                   );        // 
            Pattern pPerfCounters           = Pattern.compile("^(?:\\s*ZBXD.)?.*counters\\[(.+)\\]"                          );        // 
            Pattern pVmFolder               = Pattern.compile("^(?:\\s*ZBXD.)?.*vm\\.folder\\[(.+)\\]"                       );        // 
            Pattern pVmStorageCommitted     = Pattern.compile("^(?:\\s*ZBXD.)?.*vm\\.storage\\.committed\\[(.+)\\]"          );        // 
            Pattern pVmStorageUncommitted   = Pattern.compile("^(?:\\s*ZBXD.)?.*vm\\.storage\\.uncommitted\\[(.+)\\]"        );        // 
            Pattern pVmStorageUnshared      = Pattern.compile("^(?:\\s*ZBXD.)?.*vm\\.storage\\.unshared\\[(.+)\\]"           );        // 
            Pattern pVmGuestFullName        = Pattern.compile("^(?:\\s*ZBXD.)?.*vm\\.guest\\.os\\[(.+)\\]"                   );        // 
            Pattern pVmGuestHostName        = Pattern.compile("^(?:\\s*ZBXD.)?.*vm\\.guest\\.name\\[(.+)\\]"                 );        // 
            Pattern pVmGuestDisks           = Pattern.compile("^(?:\\s*ZBXD.)?.*vm\\.guest\\.disk\\.all\\[(.+)\\]"           );        // 
            Pattern pVmGuestDiskCapacity    = Pattern.compile("^(?:\\s*ZBXD.)?.*vm\\.guest\\.disk\\.capacity\\[(.+),(.+)\\]" );        // 
            Pattern pVmGuestDiskFreeSpace   = Pattern.compile("^(?:\\s*ZBXD.)?.*vm\\.guest\\.disk\\.free\\[(.+),(.+)\\]"     );        // 
            Pattern pVmPerfCounterValue     = Pattern.compile("^(?:\\s*ZBXD.)?.*vm\\.counter\\[([^,]+),([^,]+)(?:,([^,]*))?(?:,([^,]*))?\\]"          );        
            Pattern pVmGuestIpAddress       = Pattern.compile("^(?:\\s*ZBXD.)?.*vm\\.guest\\.ip\\[(.+)\\]"                   );        // 
            Pattern pVmGuestToolsRunningStatus        = Pattern.compile("^(?:\\s*ZBXD.)?.*vm\\.guest\\.tools\\.running\\[(.+)\\]"  );  // 
            Pattern pVmGuestToolsVersionStatus        = Pattern.compile("^(?:\\s*ZBXD.)?.*vm\\.guest\\.tools\\.version\\[(.+)\\]"  );  // 
            Pattern pVmConsolidationNeeded  = Pattern.compile("^(?:\\s*ZBXD.)?.*vm\\.consolidation\\[(.+),needed\\]"         );        // 
            Pattern pVmToolsInstallerMounted= Pattern.compile("^(?:\\s*ZBXD.)?.*vm\\.guest\\.tools\\.mounted\\[(.+)\\]"      );        //      
            Pattern pDatastoreFree          = Pattern.compile("^(?:\\s*ZBXD.)?.*datastore\\.size\\[(.+),free\\]"             );        // 
            Pattern pDatastoreTotal         = Pattern.compile("^(?:\\s*ZBXD.)?.*datastore\\.size\\[(.+),total\\]"            );        // 
            Pattern pDatastoreProvisioned   = Pattern.compile("^(?:\\s*ZBXD.)?.*datastore\\.size\\[(.+),provisioned\\]"      );        // 
            Pattern pDatastoreUncommitted   = Pattern.compile("^(?:\\s*ZBXD.)?.*datastore\\.size\\[(.+),uncommitted\\]"      );        //            

            String found;
            String[] founds;
            found = checkPattern(pPing                  ,string); if (found != null) { getPing                  (out);        return; }
            found = checkPattern(pAbout                 ,string); if (found != null) { getAbout                 (out);        return; }
            found = checkPattern(pLatestEvent           ,string); if (found != null) { getLatestEvent           (out);        return; }            
            found = checkPattern(pVMs                   ,string); if (found != null) { getVMs                   (out);        return; }
            found = checkPattern(pHosts                 ,string); if (found != null) { getHosts                 (out);        return; }
            found = checkPattern(pDatastores            ,string); if (found != null) { getDatastores            (out);        return; }            
            found = checkPattern(pHostConnection        ,string); if (found != null) { getHostConnection        (found, out); return; }
            found = checkPattern(pHostStatus            ,string); if (found != null) { getHostStatus            (found, out); return; }
            found = checkPattern(pVmStatus              ,string); if (found != null) { getVmStatus              (found, out); return; }
            found = checkPattern(pHostMaintenance       ,string); if (found != null) { getHostMaintenance       (found, out); return; }
            found = checkPattern(pHostCpuUsed           ,string); if (found != null) { getHostCpuUsed           (found, out); return; }
            found = checkPattern(pHostCpuTotal          ,string); if (found != null) { getHostCpuTotal          (found, out); return; }
            found = checkPattern(pHostDisabledPaths     ,string); if (found != null) { getHostDisabledPaths     (found, out); return; }
            found = checkPattern(pHostActivePaths       ,string); if (found != null) { getHostActivePaths       (found, out); return; }
            found = checkPattern(pHostStandbyPaths      ,string); if (found != null) { getHostStandbyPaths      (found, out); return; }
            found = checkPattern(pHostDeadPaths         ,string); if (found != null) { getHostDeadPaths         (found, out); return; }
            found = checkPattern(pHostMemUsed           ,string); if (found != null) { getHostMemUsed           (found, out); return; }
            found = checkPattern(pHostMemTotal          ,string); if (found != null) { getHostMemTotal          (found, out); return; }
            found = checkPattern(pHostMemStatsPrivate   ,string); if (found != null) { getHostVmsStatsPrivate   (found, out); return; }
            found = checkPattern(pHostMemStatsShared    ,string); if (found != null) { getHostVmsStatsShared    (found, out); return; }
            found = checkPattern(pHostMemStatsSwapped   ,string); if (found != null) { getHostVmsStatsSwapped   (found, out); return; }
            found = checkPattern(pHostMemStatsCompressed,string); if (found != null) { getHostVmsStatsCompressed(found, out); return; }
            found = checkPattern(pHostMemStatsOverhCons ,string); if (found != null) { getHostVmsStatsOverhCons (found, out); return; }
            found = checkPattern(pHostMemStatsConsumed  ,string); if (found != null) { getHostVmsStatsConsumed  (found, out); return; }
            found = checkPattern(pHostMemStatsBallooned ,string); if (found != null) { getHostVmsStatsBallooned (found, out); return; }
            found = checkPattern(pHostMemStatsActive    ,string); if (found != null) { getHostVmsStatsActive    (found, out); return; }
            founds = checkMultiplePattern(pHostPerfCounterValue   ,string); if (founds != null) { getHostPerfCounterValue         (founds,  out); return; }                        
            found = checkPattern(pVmCpuUsed             ,string); if (found != null) { getVmCpuUsed             (found, out); return; }
            found = checkPattern(pVmCpuTotal            ,string); if (found != null) { getVmCpuTotal            (found, out); return; }
            found = checkPattern(pVmCpuCores            ,string); if (found != null) { getVmCpuCores            (found, out); return; }
            found = checkPattern(pVmMemPrivate          ,string); if (found != null) { getVmMemPrivate          (found, out); return; }
            found = checkPattern(pVmMemShared           ,string); if (found != null) { getVmMemShared           (found, out); return; }
            found = checkPattern(pVmMemSwapped          ,string); if (found != null) { getVmMemSwapped          (found, out); return; }
            found = checkPattern(pVmMemCompressed       ,string); if (found != null) { getVmMemCompressed       (found, out); return; }
            found = checkPattern(pVmMemOverheadConsumed ,string); if (found != null) { getVmMemOverheadConsumed (found, out); return; }
            found = checkPattern(pVmMemConsumed         ,string); if (found != null) { getVmMemConsumed         (found, out); return; }
            found = checkPattern(pVmMemBallooned        ,string); if (found != null) { getVmMemBallooned        (found, out); return; }
            found = checkPattern(pVmMemActive           ,string); if (found != null) { getVmMemActive           (found, out); return; }
            found = checkPattern(pVmMemSize             ,string); if (found != null) { getVmMemSize             (found, out); return; }
            found = checkPattern(pVmHost                ,string); if (found != null) { getVmHost                (found, out); return; }
            found = checkPattern(pVmPowerState          ,string); if (found != null) { getVmPowerState          (found, out); return; }
            found = checkPattern(pPerfCounters          ,string); if (found != null) { getPerfCounters          (out);        return; }
            found = checkPattern(pVmFolder              ,string); if (found != null) { getVmFolder              (found, out); return; }
            found = checkPattern(pVmStorageCommitted    ,string); if (found != null) { getVmStorageCommitted    (found, out); return; }
            found = checkPattern(pVmStorageUncommitted  ,string); if (found != null) { getVmStorageUncommitted  (found, out); return; }
            found = checkPattern(pVmStorageUnshared     ,string); if (found != null) { getVmStorageUnshared     (found, out); return; }
            found = checkPattern(pVmGuestFullName       ,string); if (found != null) { getVmGuestFullName       (found, out); return; }
            found = checkPattern(pVmGuestHostName       ,string); if (found != null) { getVmGuestHostName       (found, out); return; }
            found = checkPattern(pVmGuestIpAddress      ,string); if (found != null) { getVmGuestIpAddress      (found, out); return; }
            found = checkPattern(pVmGuestDisks          ,string); if (found != null) { getVmGuestDisks          (found, out); return; }
            founds = checkMultiplePattern(pVmGuestDiskCapacity    ,string); if (founds != null) { getVmGuestDiskCapacity          (founds[0], founds[1], out); return; }
            founds = checkMultiplePattern(pVmGuestDiskFreeSpace   ,string); if (founds != null) { getVmGuestDiskFreeSpace         (founds[0], founds[1], out); return; }
            founds = checkMultiplePattern(pVmPerfCounterValue     ,string); if (founds != null) { getVmPerfCounterValue           (founds,  out); return; }            
            found = checkPattern(pVmGuestToolsRunningStatus       ,string); if (found != null) { getVmGuestToolsRunningStatus     (found, out); return; }
            found = checkPattern(pVmGuestToolsVersionStatus       ,string); if (found != null) { getVmGuestToolsVersionStatus     (found, out); return; }
            found = checkPattern(pVmToolsInstallerMounted         ,string); if (found != null) { getVmToolsInstallerMounted       (found, out); return; }
            found = checkPattern(pVmConsolidationNeeded ,string); if (found != null) { getVmConsolidationNeeded (found, out); return; }
            found = checkPattern(pHostCpuCores          ,string); if (found != null) { getHostCpuCores          (found, out); return; }
            found = checkPattern(pDatastoreFree         ,string); if (found != null) { getDatastoreSizeFree     (found, out); return; }
            found = checkPattern(pDatastoreTotal        ,string); if (found != null) { getDatastoreSizeTotal    (found, out); return; }
            found = checkPattern(pDatastoreProvisioned  ,string); if (found != null) { getDatastoreSizeProvisioned (found, out); return; } 
            found = checkPattern(pDatastoreUncommitted  ,string); if (found != null) { getDatastoreSizeUncommitted (found, out); return; }             
            
            System.out.println("String '" + string + "' not supported");
            out.print("ZBX_NOTSUPPORTED\n");
            out.flush();
        }
        
        private Boolean reconnectRequred             (ManagedEntity me                ) throws IOException {
            Boolean required = false;
            if (me == null) {
                // System.out.println("No host or vm found, checking connection");
                ManagedEntity[] mes = inventoryNavigator.searchManagedEntities("HostSystem");
                if(mes==null || mes.length == 0){
                    System.out.println("No hosts found, connection seems to be brken, attempting reconnect");
                    Request request = VmBix.updateConnectionSafe();
                    serviceInstance    = request.serviceInstance;
                    inventoryNavigator = request.inventoryNavigator;
                    required = true;
                }
                // else !!
                //     System.out.println("At least one host found, connection seems to be fine");
                // !!
            }
            return required;
        }
        
        private ManagedEntity getManagedEntityByName (String vmName, String meType    ) throws IOException {
            ManagedEntity me = inventoryNavigator.searchManagedEntity(meType, vmName);
            if (reconnectRequred(me)){
                me = inventoryNavigator.searchManagedEntity(meType, vmName);
            }
            return me;
        }

        private ManagedEntity[] getManagedEntities (String meType    ) throws IOException {
            ManagedEntity[] mes = inventoryNavigator.searchManagedEntities(meType);
            //if (reconnectRequred(mes)){
            //    mes = inventoryNavigator.searchManagedEntities(meType);
            //}
            return mes;
        }
        
       /**
        * Always return "1"
        */
        private void getPing                   (PrintWriter out                 ) throws IOException {
            out.print("1");
            out.flush();
            
        }
        
       /**
        * Returns the CPU power of a host in MHz
        */
        private Integer getHostMHZ                   (HostSystem host                 ) throws IOException {
            HostListSummary hls = host.getSummary();
            HostHardwareSummary hosthwi = hls.getHardware();
            Integer mhz = hosthwi.getCpuMhz();
            if (mhz == null) {
                mhz = 0;
            }
            return mhz;
        }
        
       /**
        * Returns the connection state of a host
        */        
        private void getHostConnection                   (String hostName, PrintWriter out) throws IOException {
            long start = System.currentTimeMillis();
            HostSystem host = (HostSystem)getManagedEntityByName(hostName,"HostSystem");
            Integer intStatus;
            if (host == null) {
                long end = System.currentTimeMillis();
                System.out.print("No host named '" + hostName + "' found\n");
                // request took:" + (end-start) + "\n");
                intStatus = 3;
            } else {
                HostRuntimeInfo hrti = host.getRuntime();
                HostSystemConnectionState hscs = hrti.getConnectionState();
                if ((hscs.name()).equals("connected")){
                    intStatus = 0;
                } else if ((hscs.name()).equals("disconnected")) {
                    intStatus = 1;
                } else {
                    intStatus = 2;
                }
                long end = System.currentTimeMillis();
            }
            out.print(intStatus );
            out.flush();
        }
        
       /**
        * Returns the status of a host
        * 0 -> grey
        * 1 -> green
        * 2 -> yellow
        * 3 -> red
        * 4 -> unknown
        */                
        private void getHostStatus                   (String hostName, PrintWriter out) throws IOException {
            long start = System.currentTimeMillis();
            HostSystem host = (HostSystem)getManagedEntityByName(hostName,"HostSystem");
            Integer intStatus;
            if (host == null) {
                long end = System.currentTimeMillis();
                System.out.print("No host named '" + hostName + "' found\n");
                // request took:" + (end-start) + "\n");
                intStatus = 3;
            } else {
                HostListSummary hsum = host.getSummary();
                String hs = hsum.getOverallStatus().toString();
                if (hs.equals("grey")){
                    intStatus = 0;
                } else if (hs.equals("green")){
                    intStatus = 1;
                } else if (hs.equals("yellow")){
                    intStatus = 2;
                } else if (hs.equals("red")){
                    intStatus = 3;                    
                } else {
                    intStatus = 4;
                }
            }
            out.print(intStatus );
            out.flush();
        }   

       /**
        * Returns the number of dead paths to the storage of a host
        */            
        private void getHostDeadPaths                   (String hostName, PrintWriter out) throws IOException {
            long start = System.currentTimeMillis();
            HostSystem host = (HostSystem)getManagedEntityByName(hostName,"HostSystem");
            Integer nb = 0;
            if (host == null) {
                long end = System.currentTimeMillis();
                System.out.print("No host named '" + hostName + "' found\n");
                // request took:" + (end-start) + "\n");
            } else {
                HostConfigInfo hc = host.getConfig();
                HostMultipathStateInfoPath[] mp = hc.getMultipathState().getPath();
                for(int m=0; m<mp.length; m++) {
                  if (mp[m].getPathState().equals("dead")){
                      nb++;
                  }
                }
            }
            out.print(nb );
            out.flush();
        }         

       /**
        * Returns the number of active paths to the storage of a host
        */              
        private void getHostActivePaths                   (String hostName, PrintWriter out) throws IOException {
            long start = System.currentTimeMillis();
            HostSystem host = (HostSystem)getManagedEntityByName(hostName,"HostSystem");
            Integer nb = 0;
            if (host == null) {
                long end = System.currentTimeMillis();
                System.out.print("No host named '" + hostName + "' found\n");
                // request took:" + (end-start) + "\n");
            } else {
                HostConfigInfo hc = host.getConfig();
                HostMultipathStateInfoPath[] mp = hc.getMultipathState().getPath();
                for(int m=0; m<mp.length; m++) {
                  if (mp[m].getPathState().equals("active")){
                      nb++;
                  }
                }
            }
            out.print(nb );
            out.flush();
        }

       /**
        * Returns the number of standby paths to the storage of a host
        */      
        private void getHostStandbyPaths                   (String hostName, PrintWriter out) throws IOException {
            long start = System.currentTimeMillis();
            HostSystem host = (HostSystem)getManagedEntityByName(hostName,"HostSystem");
            Integer nb = 0;
            if (host == null) {
                long end = System.currentTimeMillis();
                System.out.print("No host named '" + hostName + "' found\n");
                // request took:" + (end-start) + "\n");
            } else {
                HostConfigInfo hc = host.getConfig();
                HostMultipathStateInfoPath[] mp = hc.getMultipathState().getPath();
                for(int m=0; m<mp.length; m++) {
                  if (mp[m].getPathState().equals("standby")){
                      nb++;
                  }
                }
            }
            out.print(nb );
            out.flush();
        }    

       /**
        * Returns the number of disabled paths to the storage of a host
        */      
        private void getHostDisabledPaths                   (String hostName, PrintWriter out) throws IOException {
            long start = System.currentTimeMillis();
            HostSystem host = (HostSystem)getManagedEntityByName(hostName,"HostSystem");
            Integer nb = 0;
            if (host == null) {
                long end = System.currentTimeMillis();
                System.out.print("No host named '" + hostName + "' found\n");
                // request took:" + (end-start) + "\n");
            } else {
                HostConfigInfo hc = host.getConfig();
                HostMultipathStateInfoPath[] mp = hc.getMultipathState().getPath();
                for(int m=0; m<mp.length; m++) {
                  if (mp[m].getPathState().equals("disabled")){
                      nb++;
                  }
                }
            }
            out.print(nb );
            out.flush();
        }

       /**
        * Returns the status of a virtual machine
        */         
        private void getVmStatus                   (String vmName, PrintWriter out) throws IOException {
            long start = System.currentTimeMillis();
            VirtualMachine vm = (VirtualMachine)getManagedEntityByName(vmName,"VirtualMachine");
            Integer intStatus;
            if (vm == null) {
                long end = System.currentTimeMillis();
                System.out.print("No vm named '" + vmName + "' found\n");
                intStatus = 4;
            } else {
                VirtualMachineSummary vsum = vm.getSummary();
                String vs = vsum.getOverallStatus().toString();
                if (vs.equals("grey")){
                    intStatus = 0;
                } else if (vs.equals("green")){
                    intStatus = 1;
                } else if (vs.equals("yellow")){
                    intStatus = 2;
                } else if (vs.equals("red")){
                    intStatus = 3;                    
                } else {
                    intStatus = 4;
                }
                long end = System.currentTimeMillis();
            }
            out.print(intStatus );
            out.flush();
        }

       /**
        * Returns the maintenance state of a host
        */           
        private void getHostMaintenance                   (String hostName, PrintWriter out) throws IOException {
            long start = System.currentTimeMillis();
            HostSystem host = (HostSystem)getManagedEntityByName(hostName,"HostSystem");
            Boolean is;
            if (host == null) {
                long end = System.currentTimeMillis();
                System.out.print("No host named '" + hostName + "' found\n");
                is = false;
            } else {
                HostRuntimeInfo hrti = host.getRuntime();
                is = hrti.isInMaintenanceMode();
                if (is == null) { is = false; }
                long end = System.currentTimeMillis();
            }
            out.print(is?"1":"0" );
            out.flush();
        }
        
       /**
        * Returns a JSON-formatted array with the virtual machines list
        * for use with Zabbix low-level discovery
        */           
        private void getVMs                   (PrintWriter out) throws IOException {
            long start = System.currentTimeMillis();
            ManagedEntity[] vms = getManagedEntities("VirtualMachine");
            JsonArray jArray = new JsonArray();
            for(int j=0; j<vms.length; j++)
            {
                VirtualMachine vm = (VirtualMachine) vms[j];
                JsonObject jObject = new JsonObject();
                jObject.addProperty("{#VIRTUALMACHINE}", vm.getName());
                jArray.add(jObject);
            }
            JsonObject jOutput = new JsonObject();
            jOutput.add("data", jArray);
            out.print(jOutput );
            out.flush();
        }

       /**
        * Returns a JSON-formatted array with the hosts list
        * for use with Zabbix low-level discovery
        */         
        private void getHosts                   (PrintWriter out) throws IOException {
            long start = System.currentTimeMillis();
            ManagedEntity[] hs = getManagedEntities("HostSystem");
            JsonArray jArray = new JsonArray();
            for(int j=0; j<hs.length; j++)
            {
                HostSystem h = (HostSystem) hs[j];
                JsonObject jObject = new JsonObject();
                jObject.addProperty("{#ESXHOST}", h.getName());
                jArray.add(jObject);
            }
            JsonObject jOutput = new JsonObject();
            jOutput.add("data", jArray);
            out.print(jOutput );
            out.flush();
        }

       /**
        * Returns a JSON-formatted array with the datastores list
        * for use with Zabbix low-level discovery
        */ 
        private void getDatastores                   (PrintWriter out) throws IOException {
            long start = System.currentTimeMillis();
            ManagedEntity[] ds = getManagedEntities("Datastore");
            JsonArray jArray = new JsonArray();
            for(int j=0; j<ds.length; j++)
            {
                Datastore d = (Datastore) ds[j];
                JsonObject jObject = new JsonObject();
                jObject.addProperty("{#DATASTORE}", d.getName());
                jArray.add(jObject);
            }
            JsonObject jOutput = new JsonObject();
            jOutput.add("data", jArray);
            out.print(jOutput );
            out.flush();
        }        
        
       /**
        * Returns the CPU usage of a host
        */             
        private void getHostCpuUsed                  (String hostName, PrintWriter out) throws IOException {
            long start = System.currentTimeMillis();
            HostSystem host = (HostSystem)getManagedEntityByName(hostName,"HostSystem");
            Integer usedMhz;
            if (host == null) {
                long end = System.currentTimeMillis();
                System.out.print("No host named '" + hostName + "' found\n");
                // request took:" + (end-start) );
                usedMhz = 0;
            } else {
                HostListSummary hostSummary = host.getSummary();
                HostListSummaryQuickStats hostQuickStats = hostSummary.getQuickStats();

                // host.getResourcePool();
                usedMhz = hostQuickStats.getOverallCpuUsage();
                if (usedMhz == null) {
                    usedMhz = 0;
                }
                long end = System.currentTimeMillis();
                // System.out.println("host " + hostName + " mhz used: " + usedMhz
                // + "\nrequest took:" + (end-start));
            }
            out.print(usedMhz );
            out.flush();
        }

       /**
        * Returns the total CPU power of a host
        */             
        private void getHostCpuTotal                 (String hostName, PrintWriter out) throws IOException {
            long start = System.currentTimeMillis();
            HostSystem host = (HostSystem)getManagedEntityByName(hostName,"HostSystem");
            Integer totalMhz;
            if (host == null) {
                long end = System.currentTimeMillis();
                System.out.print("No host named '" + hostName + "' found\n");
                // request took:" + (end-start) + "\n");
                totalMhz = 0;
            } else {
                totalMhz = getHostMHZ(host);                
                long end = System.currentTimeMillis();
                // System.out.println("host " + hostName + " mhz total = " + totalMhz
                // + "\nrequest took:" + (end-start));
            }
            out.print(totalMhz );
            out.flush();
        }

       /**
        * Returns the number of CPU cores of a host
        */          
        private void getHostCpuCores                 (String hostName, PrintWriter out) throws IOException {
            long start = System.currentTimeMillis();
            HostSystem host = (HostSystem)getManagedEntityByName(hostName,"HostSystem");
            Short cores;
            if (host == null) {
                long end = System.currentTimeMillis();
                System.out.print("No host named '" + hostName + "' found\n");
                // request took:" + (end-start) + "\n");
                cores = 0;
            } else {
                HostListSummary hls = host.getSummary();
                HostHardwareSummary hosthwi = hls.getHardware();
                cores = hosthwi.getNumCpuCores();
                if (cores == null) {
                    cores = 0;
                }
                long end = System.currentTimeMillis();
                // System.out.println("host " + hostName + " cores = " + cores
                // + "\nrequest took:" + (end-start));
            }
            out.print(cores );
            out.flush();
        }

       /**
        * Returns the memory usage of a host
        */          
        private void getHostMemUsed                  (String hostName, PrintWriter out) throws IOException {
            long start = System.currentTimeMillis();
            HostSystem host = (HostSystem)getManagedEntityByName(hostName,"HostSystem");
            Integer usedMB;
            if (host == null) {
                long end = System.currentTimeMillis();
                System.out.print("No host named '" + hostName + "' found\n");
                // request took:" + (end-start) + "\n");
                usedMB = 0;
            } else {
                HostListSummary hostSummary = host.getSummary();
                HostListSummaryQuickStats hostQuickStats = hostSummary.getQuickStats();

                // host.getResourcePool();
                usedMB = hostQuickStats.getOverallMemoryUsage();
                if (usedMB == null) {
                    usedMB = 0;
                }
                long end = System.currentTimeMillis();
                // System.out.println("host " + hostName + " memory used: " + usedMhz
                // + "\nrequest took:" + (end-start));
            }
            out.print(usedMB );
            out.flush();
        }

       /**
        * Returns the total memory of a host
        */         
        private void getHostMemTotal                 (String hostName, PrintWriter out) throws IOException {
            long start = System.currentTimeMillis();
            HostSystem host = (HostSystem)getManagedEntityByName(hostName,"HostSystem");
            Long totalMemBytes;
            if (host == null) {
                long end = System.currentTimeMillis();
                System.out.print("No host named '" + hostName + "' found\n");
                // request took:" + (end-start) + "\n");
                totalMemBytes = new Long(0);
            } else {
                HostListSummary hls = host.getSummary();
                HostHardwareSummary hosthwi = hls.getHardware();

                totalMemBytes = hosthwi.getMemorySize();
                if (totalMemBytes == null) {
                    totalMemBytes = new Long(0);
                }
                long end = System.currentTimeMillis();
                // System.out.println("host " + hostName + " memory installed: " + totalMemBytes
                // + "\nrequest took:" + (end-start));
            }
            out.print(totalMemBytes );
            out.flush();
        }

       /**
        * Returns the virtual machines private memory usage of a host
        */         
        private void getHostVmsStatsPrivate          (String hostName, PrintWriter out) throws IOException {
            long start = System.currentTimeMillis();
            HostSystem host = (HostSystem)getManagedEntityByName(hostName,"HostSystem");
            Integer amount;
            int sum = 0;
            int activeVms = 0;
            if (host == null) {
                long end = System.currentTimeMillis();
                System.out.print("No host named '" + hostName + "' found\n");
                amount = 0;
            } else {
                VirtualMachine[] vms = (host.getVms());
                for (VirtualMachine vm:vms) {
                    VirtualMachineSummary vmSummary = vm.getSummary();
                    if (vm.getRuntime().getPowerState().name().equals("poweredOn")){
                        sum += vmSummary.getQuickStats().getPrivateMemory() * 100 / vmSummary.getConfig().getMemorySizeMB();
                        activeVms++;
                    }
                }
                amount = activeVms == 0 ? 0 : sum/activeVms;
            }
            out.print(amount );
            out.flush();
        }

       /**
        * Returns the virtual machines shared memory usage of a host
        */              
        private void getHostVmsStatsShared           (String hostName, PrintWriter out) throws IOException {
            long start = System.currentTimeMillis();
            HostSystem host = (HostSystem)getManagedEntityByName(hostName,"HostSystem");
            Integer amount;
            int sum = 0;
            int activeVms = 0;
            if (host == null) {
                long end = System.currentTimeMillis();
                System.out.print("No host named '" + hostName + "' found\n");
                amount = 0;
            } else {
                VirtualMachine[] vms = (host.getVms());
                for (VirtualMachine vm:vms) {
                    VirtualMachineSummary vmSummary = vm.getSummary();
                    if (vm.getRuntime().getPowerState().name().equals("poweredOn")){
                        sum += vmSummary.getQuickStats().getSharedMemory() * 100 / vmSummary.getConfig().getMemorySizeMB();
                        activeVms++;
                    }
                }
                amount = activeVms == 0 ? 0 : sum/activeVms;
            }
            out.print(amount );
            out.flush();
        }

       /**
        * Returns the virtual machines swapped memory usage of a host
        */              
        private void getHostVmsStatsSwapped          (String hostName, PrintWriter out) throws IOException {
            long start = System.currentTimeMillis();
            HostSystem host = (HostSystem)getManagedEntityByName(hostName,"HostSystem");
            Integer amount;
            int sum = 0;
            int activeVms = 0;
            if (host == null) {
                long end = System.currentTimeMillis();
                System.out.print("No host named '" + hostName + "' found\n");
                amount = 0;
            } else {
                VirtualMachine[] vms = (host.getVms());
                int sharedMb;
                for (VirtualMachine vm:vms) {
                    VirtualMachineSummary vmSummary = vm.getSummary();
                    if (vm.getRuntime().getPowerState().name().equals("poweredOn")){
                        sharedMb = (int)(vmSummary.getQuickStats().getSwappedMemory() / 1024);
                        sum += sharedMb * 100 / vmSummary.getConfig().getMemorySizeMB();
                        activeVms++;
                    }
                }
                amount = activeVms == 0 ? 0 : sum/activeVms;
            }
            out.print(amount );
            out.flush();
        }

       /**
        * Returns the virtual machines compressed memory usage of a host
        */              
        private void getHostVmsStatsCompressed       (String hostName, PrintWriter out) throws IOException {
            long start = System.currentTimeMillis();
            HostSystem host = (HostSystem)getManagedEntityByName(hostName,"HostSystem");
            Integer amount;
            int sum = 0;
            int activeVms = 0;
            if (host == null) {
                long end = System.currentTimeMillis();
                System.out.print("No host named '" + hostName + "' found\n");
                amount = 0;
            } else {
                VirtualMachine[] vms = (host.getVms());
                for (VirtualMachine vm:vms) {
                    VirtualMachineSummary vmSummary = vm.getSummary();
                    if (vm.getRuntime().getPowerState().name().equals("poweredOn")){
                        sum += (vmSummary.getQuickStats().getCompressedMemory() / 1024)* 100 / vmSummary.getConfig().getMemorySizeMB();
                        activeVms++;
                    }
                }
                amount = activeVms == 0 ? 0 : sum/activeVms;
            }
            out.print(amount );
            out.flush();
        }

       /**
        * Returns the virtual machines overhead memory usage of a host
        */              
        private void getHostVmsStatsOverhCons        (String hostName, PrintWriter out) throws IOException {
            long start = System.currentTimeMillis();
            HostSystem host = (HostSystem)getManagedEntityByName(hostName,"HostSystem");
            Integer amount;
            int sum = 0;
            int activeVms = 0;
            if (host == null) {
                long end = System.currentTimeMillis();
                System.out.print("No host named '" + hostName + "' found\n");
                amount = 0;
            } else {
                VirtualMachine[] vms = (host.getVms());
                for (VirtualMachine vm:vms) {
                    VirtualMachineSummary vmSummary = vm.getSummary();
                    if (vm.getRuntime().getPowerState().name().equals("poweredOn")){
                        sum += vmSummary.getQuickStats().getConsumedOverheadMemory() * 100 / vmSummary.getConfig().getMemorySizeMB();
                        activeVms++;
                    }
                }
                amount = activeVms == 0 ? 0 : sum/activeVms;
            }
            out.print(amount );
            out.flush();
        }

       /**
        * Returns the virtual machines memory usage of a host
        */              
        private void getHostVmsStatsConsumed         (String hostName, PrintWriter out) throws IOException {
            long start = System.currentTimeMillis();
            HostSystem host = (HostSystem)getManagedEntityByName(hostName,"HostSystem");
            Integer amount;
            int sum = 0;
            int activeVms = 0;
            if (host == null) {
                long end = System.currentTimeMillis();
                System.out.print("No host named '" + hostName + "' found\n");
                amount = 0;
            } else {
                VirtualMachine[] vms = (host.getVms());
                for (VirtualMachine vm:vms) {
                    VirtualMachineSummary vmSummary = vm.getSummary();
                    if (vm.getRuntime().getPowerState().name().equals("poweredOn")){
                        sum += vmSummary.getQuickStats().getHostMemoryUsage() * 100 / vmSummary.getConfig().getMemorySizeMB();
                        activeVms++;
                    }
                }
                amount = activeVms == 0 ? 0 : sum/activeVms;
            }
            out.print(amount );
            out.flush();
        }

       /**
        * Returns the virtual machines ballooned memory usage of a host
        */              
        private void getHostVmsStatsBallooned         (String hostName, PrintWriter out) throws IOException {
            long start = System.currentTimeMillis();
            HostSystem host = (HostSystem)getManagedEntityByName(hostName,"HostSystem");
            Integer amount;
            int sum = 0;
            int activeVms = 0;
            if (host == null) {
                long end = System.currentTimeMillis();
                System.out.print("No host named '" + hostName + "' found\n");
                amount = 0;
            } else {
                VirtualMachine[] vms = (host.getVms());
                for (VirtualMachine vm:vms) {
                    VirtualMachineSummary vmSummary = vm.getSummary();
                    if (vm.getRuntime().getPowerState().name().equals("poweredOn")){
                        sum += vmSummary.getQuickStats().getBalloonedMemory() * 100 / vmSummary.getConfig().getMemorySizeMB();
                        activeVms++;
                    }
                }
                amount = activeVms == 0 ? 0 : sum/activeVms;
            }
            out.print(amount );
            out.flush();
        }
        
       /**
        * Returns the virtual machines active memory usage of a host
        */              
        private void getHostVmsStatsActive           (String hostName, PrintWriter out) throws IOException {
            long start = System.currentTimeMillis();
            HostSystem host = (HostSystem)getManagedEntityByName(hostName,"HostSystem");
            Integer amount;
            int sum = 0;
            int activeVms = 0;
            if (host == null) {
                long end = System.currentTimeMillis();
                System.out.print("No host named '" + hostName + "' found\n");
                amount = 0;
            } else {
                VirtualMachine[] vms = (host.getVms());
                for (VirtualMachine vm:vms) {
                    VirtualMachineSummary vmSummary = vm.getSummary();
                    if (vm.getRuntime().getPowerState().name().equals("poweredOn")){
                        sum += vmSummary.getQuickStats().getGuestMemoryUsage() * 100 / vmSummary.getConfig().getMemorySizeMB();
                        activeVms++;
                    }
                }
                amount = activeVms == 0 ? 0 : sum/activeVms;
            }
            out.print(amount );
            out.flush();
        }

       /**
        * Returns the CPU usage of a virtual machine
        */          
        private void getVmCpuUsed                    (String vmName,   PrintWriter out) throws IOException {
            long start = System.currentTimeMillis();
            VirtualMachine vm = (VirtualMachine)getManagedEntityByName(vmName,"VirtualMachine");
            Integer usedMhz;
            if (vm == null) {
                long end = System.currentTimeMillis();
                System.out.print("No vm named '" + vmName + "' found\n");
                // request took:" + (end-start) + "\n");
                usedMhz = 0;
            } else {
                VirtualMachineSummary vmSummary = vm.getSummary();
                VirtualMachineQuickStats vmQuickStats = vmSummary.getQuickStats();

                // vm.getResourcePool();
                usedMhz = vmQuickStats.getOverallCpuUsage();
                if (usedMhz == null) {
                    usedMhz = 0;
                }
                long end = System.currentTimeMillis();
                // System.out.println("vm " + vmName + " MHZ used: " + usedMhz
                // + "\nrequest took:" + (end-start));
            }

            out.print(usedMhz );
            out.flush();
        }

       /**
        * Returns the total CPU of a virtual machine in MHz
        */          
        private void getVmCpuTotal                   (String vmName,   PrintWriter out) throws IOException {
            long start = System.currentTimeMillis();
            VirtualMachine vm = (VirtualMachine)getManagedEntityByName(vmName,"VirtualMachine");
            Integer mhz;
            if (vm == null) {
                long end = System.currentTimeMillis();
                System.out.print("No vm named '" + vmName + "' found\n");
                // request took:" + (end-start) + "\n");
                mhz = 0;
            } else {
                VirtualMachineRuntimeInfo vmrti = vm.getRuntime();
                ManagedObjectReference hostMor = vmrti.getHost();
                if (hostMor == null) { return; }
                ManagedEntity me = MorUtil.createExactManagedEntity(serviceInstance.getServerConnection(), hostMor);
                //HostSystem 
                HostSystem host = (HostSystem)me;
                
                mhz = getHostMHZ(host);

                // long hz = ((host.getHardware()).getCpuInfo()).getHz();

                // System.out.println("hostname =  + host.getName() +  : " + mhz + "mhz" 
                // // + " & " + hz + "hz"
                // + "\n it took " + (System.currentTimeMillis() - start) );
            }
            out.print(mhz );
            out.flush();
        }

       /**
        * Returns the number of CPU cores of a virtual machine
        */  
        private void getVmCpuCores                   (String vmName,   PrintWriter out) throws IOException {
            long start = System.currentTimeMillis();
            VirtualMachine vm = (VirtualMachine)getManagedEntityByName(vmName,"VirtualMachine");
            Integer cores;
            if (vm == null) {
                long end = System.currentTimeMillis();
                System.out.print("No vm named '" + vmName + "' found\n");
                // request took:" + (end-start) + "\n");
                cores = 0;
            } else {
                VirtualMachineConfigInfo vmcfg = vm.getConfig();
                VirtualHardware vmhd = vmcfg.getHardware();
                cores = vmhd.getNumCPU();
                if (cores == null) {
                    cores = 0;
                }
                long end = System.currentTimeMillis();
            }
            out.print(cores );
            out.flush();
        }        

       /**
        * Returns the power state of a virtual machine
        */          
        private void getVmPowerState                   (String vmName,   PrintWriter out) throws IOException {
            long start = System.currentTimeMillis();
            VirtualMachine vm = (VirtualMachine)getManagedEntityByName(vmName,"VirtualMachine");
            Integer intStatus;
            if (vm == null) {
                long end = System.currentTimeMillis();
                System.out.print("No vm named '" + vmName + "' found\n");
                intStatus = 0;
            } else {
                VirtualMachineRuntimeInfo vmrti = vm.getRuntime();
                String pState = vmrti.getPowerState().toString();
                if (pState.equals("poweredOff")) {
                  intStatus = 0;
                } else if (pState.equals("poweredOn")) {
                  intStatus = 1;
                } else if (pState.equals("suspended")) {
                  intStatus = 2;                    
                } else {
                  intStatus = 3;
                }
                long end = System.currentTimeMillis();

            }
            out.print(intStatus );
            out.flush();
        }

       /**
        * Returns a performance counter value for a virtual machine
        * The params array contains :
        * - the VM name
        * - the performance counter name
        * - an optional instance name (for example "nic0")
        * - an optional query interval (default is defined in the configuration file with the "interval" keyword)
        * We don't query the vCenter for historical, but real-time data.
        * So as to collect the most accurate data, we gather a series of real-time (20s interval)
        * values over the defined interval. For rate and absolute values, we calculate the average value.
        * For delta values, we sum the results.
        */          
        private void getVmPerfCounterValue (String[] params, PrintWriter out) throws IOException {
            String vmName = params[0];
            String perfCounterName = params[1];
            String instanceName = "";
            if (params[2] != null) {
              instanceName = params[2];
            }
            Integer newInterval = interval;
            if (params[3] != null) {
              newInterval = Integer.parseInt(params[3]);
            }
            long start = System.currentTimeMillis();
            VirtualMachine vm = (VirtualMachine)getManagedEntityByName(vmName,"VirtualMachine");
            Long intValue;
            if (vm == null) {
                long end = System.currentTimeMillis();
                System.out.print("No vm named '" + vmName + "' found\n");
            } else {
              VirtualMachineRuntimeInfo vmrti = vm.getRuntime();
              String pState = vmrti.getPowerState().toString();
              if (pState.equals("poweredOn")) {
                PerformanceManager performanceManager = serviceInstance.getPerformanceManager();

                // find out the refresh rate for the virtual machine
                PerfProviderSummary pps = performanceManager.queryPerfProviderSummary(vm);

                // retrieve all the available performance counters
                PerfCounterInfo[] pcis = performanceManager.getPerfCounter();
                Hashtable ctrTable = new Hashtable(pcis.length * 2);
                Hashtable typeTable = new Hashtable(pcis.length * 2);
                for(int i = 0; i < pcis.length; i++) {
                  String perfCounter  = pcis[i].getGroupInfo().getKey() + "." + pcis[i].getNameInfo().getKey();
                  Integer perfKey = pcis[i].getKey();
                  ctrTable.put(perfCounter, perfKey); 
                  
                  String perfType = pcis[i].getStatsType().toString();
                  typeTable.put(perfCounter, perfType); 
                }
                Integer perfCounterId = (Integer)ctrTable.get(perfCounterName);
                String perfCounterType = (String)typeTable.get(perfCounterName);
                
                ArrayList<PerfMetricId> perfMetricIds = new ArrayList<PerfMetricId>();
                Boolean exists = true;

                PerfMetricId metricId = new PerfMetricId();
                metricId.setCounterId(perfCounterId);
                metricId.setInstance(instanceName);
                perfMetricIds.add(metricId);
                
                if (exists == false) {
                  System.out.println("Metric " + perfCounterName + " doesn't exist for vm " + vmName);
                  long end = System.currentTimeMillis();
                  intValue = 0L;
                } else {
                  PerfMetricId[] pmi = perfMetricIds.toArray(new PerfMetricId[perfMetricIds.size()]);

                  PerfQuerySpec qSpec = new PerfQuerySpec();
                  Calendar previous = Calendar.getInstance();
                  Calendar current = (Calendar)previous.clone();
                  Date old = new Date(previous.getTimeInMillis() - (newInterval*1000));
                  previous.setTime(old);
                  
                  qSpec.setEntity(vm.getMOR());
                  qSpec.setStartTime(previous);
                  qSpec.setEndTime(current);
                  qSpec.setMetricId(pmi);
                  qSpec.setIntervalId(20); // real-time values
   
                  PerfEntityMetricBase[] pValues = performanceManager.queryPerf(new PerfQuerySpec[] {qSpec});
                  intValue = 0L;
                  if(pValues != null) {
                    for(int i=0; i<pValues.length; ++i) {
                      if(pValues[i] instanceof PerfEntityMetric)
                      {
                        PerfEntityMetric pem = (PerfEntityMetric)pValues[i];
                        PerfMetricSeries[] vals = pem.getValue();
                        for(int j=0; vals!=null && j<vals.length; ++j)
                        {
                          PerfMetricIntSeries val = (PerfMetricIntSeries) vals[j];
                          long[] longs = val.getValue();
                          long sum = 0;
                          //String instance = val.getId().getInstance();
                          for(int k=0; k<longs.length; k++)
                          {
                            sum = sum + longs[k];
                          }
                          if (perfCounterType.equals("delta")) {
                            intValue = sum;
                          } else {
                            // calculate average
                            intValue = sum / longs.length;
                          }
                          out.print(intValue);
                        }
                      } else {
                        System.out.println("No returned value");
                      }
                    }
                  } else {
                    System.out.println("No returned value");
                  }
                  long end = System.currentTimeMillis();
                }
              } else {
                long end = System.currentTimeMillis();
                System.out.print("VM '" + vmName + "' is not powered on. Performance counters unavailable.\n");
              }
            }
            out.flush();
        }
        
       /**
        * Returns a performance counter value for a host
        * The params array contains :
        * - the host name
        * - the performance counter name
        * - an optional instance name (for example "nic0")
        * - an optional query interval (default is defined in the configuration file with the "interval" keyword)
        * We don't query the vCenter for historical, but real-time data.
        * So as to collect the most accurate data, we gather a series of real-time (20s interval)
        * values over the defined interval. For rate and absolute values, we calculate the average value.
        * For delta values, we sum the results.
        */          
        private void getHostPerfCounterValue (String[] params, PrintWriter out) throws IOException {
            String hostName = params[0];
            String perfCounterName = params[1];
            String instanceName = "";
            if (params[2] != null) {
              instanceName = params[2];
            }
            Integer newInterval = interval;
            if (params[3] != null) {
              newInterval = Integer.parseInt(params[3]);
            }
            long start = System.currentTimeMillis();
            HostSystem host = (HostSystem)getManagedEntityByName(hostName,"HostSystem");
            Long intValue;
            if (host == null) {
                long end = System.currentTimeMillis();
                System.out.print("No host named '" + hostName + "' found\n");
            } else {
              HostRuntimeInfo hostrti = host.getRuntime();
              String pState = hostrti.getPowerState().toString();
              if (pState.equals("poweredOn")) {
                PerformanceManager performanceManager = serviceInstance.getPerformanceManager();

                // find out the refresh rate for the virtual machine
                PerfProviderSummary pps = performanceManager.queryPerfProviderSummary(host);

                // retrieve all the available performance counters
                PerfCounterInfo[] pcis = performanceManager.getPerfCounter();
                Hashtable ctrTable = new Hashtable(pcis.length * 2);
                Hashtable typeTable = new Hashtable(pcis.length * 2);
                for(int i = 0; i < pcis.length; i++) {
                  String perfCounter  = pcis[i].getGroupInfo().getKey() + "." + pcis[i].getNameInfo().getKey();
                  Integer perfKey = pcis[i].getKey();
                  ctrTable.put(perfCounter, perfKey); 
                  
                  String perfType = pcis[i].getStatsType().toString();
                  typeTable.put(perfCounter, perfType); 
                }
                Integer perfCounterId = (Integer)ctrTable.get(perfCounterName);
                String perfCounterType = (String)typeTable.get(perfCounterName);
                
                ArrayList<PerfMetricId> perfMetricIds = new ArrayList<PerfMetricId>();
                Boolean exists = true;

                PerfMetricId metricId = new PerfMetricId();
                metricId.setCounterId(perfCounterId);
                metricId.setInstance(instanceName);
                perfMetricIds.add(metricId);
                
                if (exists == false) {
                  System.out.println("Metric " + perfCounterName + " doesn't exist for host " + hostName);
                  long end = System.currentTimeMillis();
                  intValue = 0L;
                } else {
                  PerfMetricId[] pmi = perfMetricIds.toArray(new PerfMetricId[perfMetricIds.size()]);

                  PerfQuerySpec qSpec = new PerfQuerySpec();
                  Calendar previous = Calendar.getInstance();
                  Calendar current = (Calendar)previous.clone();
                  Date old = new Date(previous.getTimeInMillis() - (newInterval*1000));
                  previous.setTime(old);
                  
                  qSpec.setEntity(host.getMOR());
                  qSpec.setStartTime(previous);
                  qSpec.setEndTime(current);
                  qSpec.setMetricId(pmi);
                  qSpec.setIntervalId(20); // real-time values
   
                  PerfEntityMetricBase[] pValues = performanceManager.queryPerf(new PerfQuerySpec[] {qSpec});
                  intValue = 0L;
                  if(pValues != null) {
                    for(int i=0; i<pValues.length; ++i) {
                      if(pValues[i] instanceof PerfEntityMetric)
                      {
                        PerfEntityMetric pem = (PerfEntityMetric)pValues[i];
                        PerfMetricSeries[] vals = pem.getValue();
                        for(int j=0; vals!=null && j<vals.length; ++j)
                        {
                          PerfMetricIntSeries val = (PerfMetricIntSeries) vals[j];
                          long[] longs = val.getValue();
                          long sum = 0;
                          //String instance = val.getId().getInstance();
                          for(int k=0; k<longs.length; k++)
                          {
                            sum = sum + longs[k];
                          }
                          if (perfCounterType.equals("delta")) {
                            intValue = sum;
                          } else {
                            // calculate average
                            intValue = sum / longs.length;
                          }
                          out.print(intValue);
                        }
                      } else {
                        System.out.println("No returned value");
                      }
                    }
                  } else {
                    System.out.println("No returned value");
                  }
                  long end = System.currentTimeMillis();
                }
              } else {
                long end = System.currentTimeMillis();
                System.out.print("Host '" + hostName + "' is not powered on. Performance counters unavailable.\n");
              }
            }
            out.flush();
        }        

       /**
        * Returns the list of available performance counters of an entity
        */             
        private void getPerfCounters                   (PrintWriter out) throws IOException {
          long start = System.currentTimeMillis();
          PerformanceManager performanceManager = serviceInstance.getPerformanceManager();
          PerfCounterInfo[] perfCounters = performanceManager.getPerfCounter();
          for (int i = 0; i < perfCounters.length; i++) {
            PerfCounterInfo perfCounterInfo = perfCounters[i];
            String perfCounterString = perfCounterInfo.getGroupInfo().getKey() + "." + perfCounterInfo.getNameInfo().getKey() + " : " + perfCounterInfo.getNameInfo().getLabel() + " in " + perfCounterInfo.getUnitInfo().getLabel() + " (" + perfCounterInfo.getStatsType().toString() + ")";
            out.print(perfCounterInfo.getKey() + " : " + perfCounterString + "\n");
          }
          long end = System.currentTimeMillis();
          out.flush();
        }

       /**
        * Returns the about info of the VMWare API
        */             
        private void getAbout                   (PrintWriter out) throws IOException {
          long start = System.currentTimeMillis();
          AboutInfo about = serviceInstance.getAboutInfo();
          out.print(about.getFullName());
          long end = System.currentTimeMillis();
          out.flush();
        }

       /**
        * Returns the latest event on the vCenter
        */             
        private void getLatestEvent                   (PrintWriter out) throws IOException {
          long start = System.currentTimeMillis();
          EventManager eventManager = serviceInstance.getEventManager();
          out.print(eventManager.getLatestEvent().getFullFormattedMessage());
          long end = System.currentTimeMillis();
          out.flush();
        }        

       /**
        * Returns the vCenter folder of a virtual machine
        */             
        private void getVmFolder                   (String vmName, PrintWriter out) throws IOException {
            long start = System.currentTimeMillis();
            VirtualMachine vm = (VirtualMachine)getManagedEntityByName(vmName,"VirtualMachine");
            String vmFolder = "";
            if (vm == null) {
                long end = System.currentTimeMillis();
                System.out.print("No vm named '" + vmName + "' found\n");
            } else {
                ManagedEntity fd = vm.getParent();
                while (fd instanceof Folder) {
                  vmFolder = "/" + fd.getName() + vmFolder;
                  fd = fd.getParent();
                }
                long end = System.currentTimeMillis();
            }
            out.print(vmFolder );
            out.flush();
        }        

       /**
        * Returns the private memory of a virtual machine
        */             
        private void getVmMemPrivate                 (String vmName,   PrintWriter out) throws IOException {
            long start = System.currentTimeMillis();
            VirtualMachine vm = (VirtualMachine)getManagedEntityByName(vmName,"VirtualMachine");
            Integer amount;
            if (vm == null) {
                long end = System.currentTimeMillis();
                System.out.print("No vm named '" + vmName + "' found\n");
                amount = 0;
            } else {
                VirtualMachineSummary vmSummary = vm.getSummary();
                VirtualMachineQuickStats vmQuickStats = vmSummary.getQuickStats();

                amount = vmQuickStats.getPrivateMemory();
                if (amount == null) { amount = 0; }
                long end = System.currentTimeMillis();
            }
            out.print(amount );
            out.flush();
        }

       /**
        * Returns the shared memory of a virtual machine
        */                  
        private void getVmMemShared                  (String vmName,   PrintWriter out) throws IOException {
            long start = System.currentTimeMillis();
            VirtualMachine vm = (VirtualMachine)getManagedEntityByName(vmName,"VirtualMachine");
            Integer amount;
            if (vm == null) {
                long end = System.currentTimeMillis();
                System.out.print("No vm named '" + vmName + "' found\n");
                amount = 0;
            } else {
                VirtualMachineSummary vmSummary = vm.getSummary();
                VirtualMachineQuickStats vmQuickStats = vmSummary.getQuickStats();

                amount = vmQuickStats.getSharedMemory();
                if (amount == null) { amount = 0; }
                long end = System.currentTimeMillis();
            }
            out.print(amount );
            out.flush();
        }

       /**
        * Returns the swapped memory of a virtual machine
        */          
        private void getVmMemSwapped                 (String vmName,   PrintWriter out) throws IOException {
            long start = System.currentTimeMillis();
            VirtualMachine vm = (VirtualMachine)getManagedEntityByName(vmName,"VirtualMachine");
            Integer amount;
            if (vm == null) {
                long end = System.currentTimeMillis();
                System.out.print("No vm named '" + vmName + "' found\n");
                amount = 0;
            } else {
                VirtualMachineSummary vmSummary = vm.getSummary();
                VirtualMachineQuickStats vmQuickStats = vmSummary.getQuickStats();

                amount = vmQuickStats.getSwappedMemory();
                if (amount == null) { amount = 0; }
                long end = System.currentTimeMillis();
            }
            out.print(amount );
            out.flush();
        }

       /**
        * Returns the compressed memory of a virtual machine
        */                  
        private void getVmMemCompressed              (String vmName,   PrintWriter out) throws IOException {
            long start = System.currentTimeMillis();
            VirtualMachine vm = (VirtualMachine)getManagedEntityByName(vmName,"VirtualMachine");
            Long amount;
            if (vm == null) {
                long end = System.currentTimeMillis();
                System.out.print("No vm named '" + vmName + "' found\n");
                amount = new Long(0);
            } else {
                VirtualMachineSummary vmSummary = vm.getSummary();
                VirtualMachineQuickStats vmQuickStats = vmSummary.getQuickStats();

                amount = vmQuickStats.getCompressedMemory();
                if (amount == null) { amount = new Long(0); }
                long end = System.currentTimeMillis();
            }
            out.print(amount );
            out.flush();
        }

       /**
        * Returns the overhead memory of a virtual machine
        */                  
        private void getVmMemOverheadConsumed        (String vmName,   PrintWriter out) throws IOException {
            long start = System.currentTimeMillis();
            VirtualMachine vm = (VirtualMachine)getManagedEntityByName(vmName,"VirtualMachine");
            Integer amount;
            if (vm == null) {
                long end = System.currentTimeMillis();
                System.out.print("No vm named '" + vmName + "' found\n");
                amount = 0;
            } else {
                VirtualMachineSummary vmSummary = vm.getSummary();
                VirtualMachineQuickStats vmQuickStats = vmSummary.getQuickStats();

                amount = vmQuickStats.getConsumedOverheadMemory();
                if (amount == null) { amount = 0; }
                long end = System.currentTimeMillis();
            }
            out.print(amount );
            out.flush();
        }

       /**
        * Returns the consumed memory of a virtual machine
        */                  
        private void getVmMemConsumed                (String vmName,   PrintWriter out) throws IOException {
            long start = System.currentTimeMillis();
            VirtualMachine vm = (VirtualMachine)getManagedEntityByName(vmName,"VirtualMachine");
            Integer amount;
            if (vm == null) {
                long end = System.currentTimeMillis();
                System.out.print("No vm named '" + vmName + "' found\n");
                amount = 0;
            } else {
                VirtualMachineSummary vmSummary = vm.getSummary();
                VirtualMachineQuickStats vmQuickStats = vmSummary.getQuickStats();

                amount = vmQuickStats.getHostMemoryUsage();
                if (amount == null) { amount = 0; }
                long end = System.currentTimeMillis();
            }
            out.print(amount );
            out.flush();
        }

       /**
        * Returns the ballooned memory of a virtual machine
        */                  
        private void getVmMemBallooned                (String vmName,   PrintWriter out) throws IOException {
            long start = System.currentTimeMillis();
            VirtualMachine vm = (VirtualMachine)getManagedEntityByName(vmName,"VirtualMachine");
            Integer amount;
            if (vm == null) {
                long end = System.currentTimeMillis();
                System.out.print("No vm named '" + vmName + "' found\n");
                amount = 0;
            } else {
                VirtualMachineSummary vmSummary = vm.getSummary();
                VirtualMachineQuickStats vmQuickStats = vmSummary.getQuickStats();

                amount = vmQuickStats.getBalloonedMemory();
                if (amount == null) { amount = 0; }
                long end = System.currentTimeMillis();
            }
            out.print(amount );
            out.flush();
        }

       /**
        * Returns the active memory of a virtual machine
        */                  
        private void getVmMemActive                  (String vmName,   PrintWriter out) throws IOException {
            long start = System.currentTimeMillis();
            VirtualMachine vm = (VirtualMachine)getManagedEntityByName(vmName,"VirtualMachine");
            Integer amount;
            if (vm == null) {
                long end = System.currentTimeMillis();
                System.out.print("No vm named '" + vmName + "' found\n");
                amount = 0;
            } else {
                VirtualMachineSummary vmSummary = vm.getSummary();
                VirtualMachineQuickStats vmQuickStats = vmSummary.getQuickStats();

                amount = vmQuickStats.getGuestMemoryUsage();
                if (amount == null) { amount = 0; }
                long end = System.currentTimeMillis();
            }
            out.print(amount );
            out.flush();
        }

       /**
        * Returns the consolidation status of a virtual machine
        */                  
        private void getVmConsolidationNeeded                  (String vmName,   PrintWriter out) throws IOException {
            long start = System.currentTimeMillis();
            VirtualMachine vm = (VirtualMachine)getManagedEntityByName(vmName,"VirtualMachine");
            Boolean is;
            if (vm == null) {
                long end = System.currentTimeMillis();
                System.out.print("No vm named '" + vmName + "' found\n");
                is = false;
            } else {
                VirtualMachineRuntimeInfo vmRuntime = vm.getRuntime();
                is = vmRuntime.getConsolidationNeeded();
                if (is == null) { is = false; }
                long end = System.currentTimeMillis();
            }
            out.print(is?"1":"0" );
            out.flush();
        }    

       /**
        * Returns a true if the VM Tools installer is mounted of a virtual machine
        * Returns false if not
        */          
        private void getVmToolsInstallerMounted                  (String vmName,   PrintWriter out) throws IOException {
            long start = System.currentTimeMillis();
            VirtualMachine vm = (VirtualMachine)getManagedEntityByName(vmName,"VirtualMachine");
            Boolean is;
            if (vm == null) {
                long end = System.currentTimeMillis();
                System.out.print("No vm named '" + vmName + "' found\n");
                is = false;
            } else {
                VirtualMachineRuntimeInfo vmRuntime = vm.getRuntime();
                is = vmRuntime.isToolsInstallerMounted();
                if (is == null) { is = false; }
                long end = System.currentTimeMillis();
            }
            out.print(is?"1":"0" );
            out.flush();
        }         

       /**
        * Returns the running host of a virtual machine
        */        
        private void getVmHost                  (String vmName,   PrintWriter out) throws IOException {
            long start = System.currentTimeMillis();
            VirtualMachine vm = (VirtualMachine)getManagedEntityByName(vmName,"VirtualMachine");
            String vmHost;
            if (vm == null) {
                long end = System.currentTimeMillis();
                System.out.print("No vm named '" + vmName + "' found\n");
                vmHost = "";
            } else {
                VirtualMachineRuntimeInfo vmRuntimeInfo = vm.getRuntime();
                ManagedObjectReference hmor = vmRuntimeInfo.getHost();
                HostSystem host = new HostSystem(vm.getServerConnection(), hmor);
                vmHost = host.getName();
                long end = System.currentTimeMillis();
            }
            out.print(vmHost );
            out.flush();
        }            

       /**
        * Returns the guest OS full description of a virtual machine
        */                
        private void getVmGuestFullName                  (String vmName,   PrintWriter out) throws IOException {
            long start = System.currentTimeMillis();
            VirtualMachine vm = (VirtualMachine)getManagedEntityByName(vmName,"VirtualMachine");
            String guestFullName;
            if (vm == null) {
                long end = System.currentTimeMillis();
                System.out.print("No vm named '" + vmName + "' found\n");
                guestFullName = "";
            } else {
                VirtualMachineSummary vmSummary = vm.getSummary();
                VirtualMachineGuestSummary vmGuest = vmSummary.getGuest();
                if (vmGuest == null) {
                  long end = System.currentTimeMillis();
                  System.out.print("Cannot query guest OS for VM '" + vmName + "'\n");
                  guestFullName = "";
                } else {
                  guestFullName = vmGuest.getGuestFullName();
                  long end = System.currentTimeMillis();
                }
            }
            out.print(guestFullName );
            out.flush();
        } 

       /**
        * Returns the guest OS hostname of a virtual machine
        */              
        private void getVmGuestHostName                  (String vmName,   PrintWriter out) throws IOException {
            long start = System.currentTimeMillis();
            VirtualMachine vm = (VirtualMachine)getManagedEntityByName(vmName,"VirtualMachine");
            String guestHostName;
            if (vm == null) {
                long end = System.currentTimeMillis();
                System.out.print("No vm named '" + vmName + "' found\n");
                guestHostName = "";
            } else {
                VirtualMachineSummary vmSummary = vm.getSummary();
                VirtualMachineGuestSummary vmGuest = vmSummary.getGuest();
                if (vmGuest == null) {
                  long end = System.currentTimeMillis();
                  System.out.print("Cannot query guest OS for VM '" + vmName + "'\n");
                  guestHostName = "";
                } else {
                  guestHostName = vmGuest.getHostName();
                  long end = System.currentTimeMillis();
                }
            }
            out.print(guestHostName);
            out.flush();
        }     

       /**
        * Returns the list of the guest OS disks of a virtual machine
        * Formatted in JSON for use with Zabbix LLD
        */              
        private void getVmGuestDisks                  (String vmName,   PrintWriter out) throws IOException {
            long start = System.currentTimeMillis();
            VirtualMachine vm = (VirtualMachine)getManagedEntityByName(vmName,"VirtualMachine");
            JsonArray jArray = new JsonArray();
            if (vm == null) {
                long end = System.currentTimeMillis();
                System.out.print("No vm named '" + vmName + "' found\n");
                jArray = null;
            } else {
                GuestInfo gInfo = vm.getGuest();
                if (gInfo == null) {
                  long end = System.currentTimeMillis();
                  System.out.print("Cannot query guest OS for VM '" + vmName + "'\n");   
                  jArray = null;                  
                } else {
                  GuestDiskInfo[] vmDisks = gInfo.getDisk();
                  if (vmDisks != null) {
                    for(int j=0; j<vmDisks.length; j++)
                    {
                      JsonObject jObject = new JsonObject();
                      jObject.addProperty("{#GUESTDISK}", vmDisks[j].getDiskPath());
                      jArray.add(jObject);
                    }
                  } else {
                    jArray = null;
                    long end = System.currentTimeMillis();
                  }
                }
            }
            JsonObject jOutput = new JsonObject();
            jOutput.add("data", jArray);
            out.print(jOutput );
            out.flush();
        }          

       /**
        * Returns a disk capacity for the guest OS of a virtual machine
        */      
        private void getVmGuestDiskCapacity                  (String vmName, String vmDisk,  PrintWriter out) throws IOException {
            long start = System.currentTimeMillis();
            VirtualMachine vm = (VirtualMachine)getManagedEntityByName(vmName,"VirtualMachine");
            Long size = 0L;
            if (vm == null) {
                long end = System.currentTimeMillis();
                System.out.print("No vm named '" + vmName + "' found\n");
            } else {
                GuestInfo gInfo = vm.getGuest();
                if (gInfo == null) {
                  long end = System.currentTimeMillis();
                  System.out.print("Cannot query guest OS for VM '" + vmName + "'\n");                  
                } else {
                  GuestDiskInfo[] vmDisks = gInfo.getDisk();
                  if (vmDisks != null) {
                    for(int j=0; j<vmDisks.length; j++)
                    {
                      if (vmDisks[j].getDiskPath().equals(vmDisk)) {
                        size = vmDisks[j].getCapacity();
                      }
                    }
                    long end = System.currentTimeMillis();
                  } else {
                    long end = System.currentTimeMillis();
                    System.out.print("Cannot query disks for VM '" + vmName + "'\n");                       
                  }
                }
            }
            out.print(size );
            out.flush();
        }   
       /**
        * Returns a disk free space for the guest OS of a virtual machine
        */      
        private void getVmGuestDiskFreeSpace                  (String vmName, String vmDisk,  PrintWriter out) throws IOException {
            long start = System.currentTimeMillis();
            VirtualMachine vm = (VirtualMachine)getManagedEntityByName(vmName,"VirtualMachine");
            Long size = 0L;
            if (vm == null) {
                long end = System.currentTimeMillis();
                System.out.print("No vm named '" + vmName + "' found\n");
            } else {
                GuestInfo gInfo = vm.getGuest();
                if (gInfo == null) {
                  long end = System.currentTimeMillis();
                  System.out.print("Cannot query guest OS for VM '" + vmName + "'\n");                  
                } else {
                  GuestDiskInfo[] vmDisks = gInfo.getDisk();
                  if (vmDisks != null) {
                    for(int j=0; j<vmDisks.length; j++)
                    {
                      if (vmDisks[j].getDiskPath().equals(vmDisk)) {
                        size = vmDisks[j].getFreeSpace();
                      }
                    }
                    long end = System.currentTimeMillis();
                  } else {
                    long end = System.currentTimeMillis();
                    System.out.print("Cannot query disks for VM '" + vmName + "'\n");        
                  }  
                }
            }
            out.print(size);
            out.flush();
        }                

       /**
        * Returns the guest OS IP address of a virtual machine
        */          
        private void getVmGuestIpAddress                  (String vmName,   PrintWriter out) throws IOException {
            long start = System.currentTimeMillis();
            VirtualMachine vm = (VirtualMachine)getManagedEntityByName(vmName,"VirtualMachine");
            String guestIpAddress;
            if (vm == null) {
                long end = System.currentTimeMillis();
                System.out.print("No vm named '" + vmName + "' found\n");
                guestIpAddress = "";
            } else {
                VirtualMachineSummary vmSummary = vm.getSummary();
                VirtualMachineGuestSummary vmGuest = vmSummary.getGuest();
                if (vmGuest == null) {
                  long end = System.currentTimeMillis();
                  System.out.print("Cannot query guest OS for VM '" + vmName + "'\n");
                  guestIpAddress = "";
                } else {
                  guestIpAddress = vmGuest.getIpAddress();
                  long end = System.currentTimeMillis();
                }
            }
            out.print(guestIpAddress );
            out.flush();
        }         

       /**
        * Returns the committed storage of a virtual machine
        */          
        private void getVmStorageCommitted                  (String vmName,   PrintWriter out) throws IOException {
            long start = System.currentTimeMillis();
            VirtualMachine vm = (VirtualMachine)getManagedEntityByName(vmName,"VirtualMachine");
            Long size;
            if (vm == null) {
                long end = System.currentTimeMillis();
                System.out.print("No vm named '" + vmName + "' found\n");
                size = 0L;
            } else {
                VirtualMachineSummary vmSummary = vm.getSummary();
                VirtualMachineStorageSummary vmStorage = vmSummary.getStorage();
                size = vmStorage.getCommitted();
                long end = System.currentTimeMillis();
            }
            out.print(size );
            out.flush();
        }   

       /**
        * Returns the uncommitted storage of a virtual machine
        */          
        private void getVmStorageUncommitted                  (String vmName,   PrintWriter out) throws IOException {
            long start = System.currentTimeMillis();
            VirtualMachine vm = (VirtualMachine)getManagedEntityByName(vmName,"VirtualMachine");
            Long size;
            if (vm == null) {
                long end = System.currentTimeMillis();
                System.out.print("No vm named '" + vmName + "' found\n");
                size = 0L;
            } else {
                VirtualMachineSummary vmSummary = vm.getSummary();
                VirtualMachineStorageSummary vmStorage = vmSummary.getStorage();
                size = vmStorage.getUncommitted();
                long end = System.currentTimeMillis();
            }
            out.print(size );
            out.flush();
        }   

       /**
        * Returns the unshared storage of a virtual machine
        */          
        private void getVmStorageUnshared                  (String vmName,   PrintWriter out) throws IOException {
            long start = System.currentTimeMillis();
            VirtualMachine vm = (VirtualMachine)getManagedEntityByName(vmName,"VirtualMachine");
            Long size;
            if (vm == null) {
                long end = System.currentTimeMillis();
                System.out.print("No vm named '" + vmName + "' found\n");
                size = 0L;
            } else {
                VirtualMachineSummary vmSummary = vm.getSummary();
                VirtualMachineStorageSummary vmStorage = vmSummary.getStorage();
                size = vmStorage.getUnshared();
                long end = System.currentTimeMillis();
            }
            out.print(size );
            out.flush();
        }   

       /**
        * Returns a true if the virtual machine VM Tools are up-to-date
        * Returns false if not
        */          
        private void getVmGuestToolsVersionStatus                  (String vmName,   PrintWriter out) throws IOException {
            long start = System.currentTimeMillis();
            VirtualMachine vm = (VirtualMachine)getManagedEntityByName(vmName,"VirtualMachine");
            String guestToolsVersionStatus;
            if (vm == null) {
                long end = System.currentTimeMillis();
                System.out.print("No vm named '" + vmName + "' found\n");
                guestToolsVersionStatus = "";
            } else {
                VirtualMachineSummary vmSummary = vm.getSummary();
                VirtualMachineGuestSummary vmGuest = vmSummary.getGuest();
                if (vmGuest == null) {
                  long end = System.currentTimeMillis();
                  System.out.print("Cannot query guest OS for VM '" + vmName + "'\n");
                  guestToolsVersionStatus = "";
                } else {
                  guestToolsVersionStatus = vmGuest.getToolsVersionStatus();
                  long end = System.currentTimeMillis();
                }
            }
            out.print(guestToolsVersionStatus );
            out.flush();
        }        

       /**
        * Returns a true if the virtual machine VM Tools are running
        * Returns false if not
        */          
        private void getVmGuestToolsRunningStatus                  (String vmName,   PrintWriter out) throws IOException {
            long start = System.currentTimeMillis();
            VirtualMachine vm = (VirtualMachine)getManagedEntityByName(vmName,"VirtualMachine");
            String guestToolsRunningStatus;
            if (vm == null) {
                long end = System.currentTimeMillis();
                System.out.print("No vm named '" + vmName + "' found\n");
                guestToolsRunningStatus = "";
            } else {
                VirtualMachineSummary vmSummary = vm.getSummary();
                VirtualMachineGuestSummary vmGuest = vmSummary.getGuest();
                if (vmGuest == null) {
                  long end = System.currentTimeMillis();
                  System.out.print("Cannot query guest OS for VM '" + vmName + "'\n");
                  guestToolsRunningStatus = "";
                } else {
                  guestToolsRunningStatus = vmGuest.getToolsRunningStatus();
                  long end = System.currentTimeMillis();
                }
            }
            out.print(guestToolsRunningStatus );
            out.flush();
        }         

       /**
        * Returns the memory size of a virtual machine
        */          
        private void getVmMemSize                    (String vmName,   PrintWriter out) throws IOException {
            long start = System.currentTimeMillis();
            VirtualMachine vm = (VirtualMachine)getManagedEntityByName(vmName,"VirtualMachine");
            Integer amount;
            if (vm == null) {
                long end = System.currentTimeMillis();
                System.out.print("No vm named '" + vmName + "' found\n");
                amount = 0;
            } else {
                VirtualMachineSummary vmSummary = vm.getSummary();
                VirtualMachineConfigSummary vmConfigSum = vmSummary.getConfig();

                amount = vmConfigSum.getMemorySizeMB();
                if (amount == null) { amount = 0; }
                long end = System.currentTimeMillis();
            }
            out.print(amount );
            out.flush();
        }

       /**
        * Returns the free space of a datastore
        */                  
        private void getDatastoreSizeFree            (String dsName,   PrintWriter out) throws IOException {
            long start = System.currentTimeMillis();
            Datastore ds = (Datastore)getManagedEntityByName(dsName,"Datastore");
            Long freeSpace;
            if (ds == null) {
                long end = System.currentTimeMillis();
                System.out.print("No datastore named '" + dsName + "' found\n");
                // request took:" + (end-start) );
                freeSpace = new Long(0);
            } else {
                DatastoreSummary dsSum = ds.getSummary();
                freeSpace = dsSum.getFreeSpace();
                if (freeSpace == null) {
                    freeSpace = new Long(0);
                }
                // System.out.println("store " + dsName +" free: " + freeSpace
                // + "\n it took " + (System.currentTimeMillis() - start) );
            }
            out.print(freeSpace );
            out.flush();
        }

       /**
        * Returns the size of a datastore
        */                  
        private void getDatastoreSizeTotal           (String dsName,   PrintWriter out) throws IOException {
            long start = System.currentTimeMillis();
            Datastore ds = (Datastore)getManagedEntityByName(dsName,"Datastore");
            Long capacity;
            if (ds == null) {
                long end = System.currentTimeMillis();
                System.out.print("No datastore named '" + dsName + "' found\n");
                // request took:" + (end-start) + "\n");
                capacity = new Long(0);
            } else {
                DatastoreSummary dsSum = ds.getSummary();
                capacity = dsSum.getCapacity();
                if (capacity == null) {
                    capacity = new Long(0);
                }
                // System.out.println("store " + dsName +" free: " + capacity
                // + "\n it took " + (System.currentTimeMillis() - start) );
            }
            out.print(capacity );
            out.flush();
        }

       /**
        * Returns the provisioned size of a datastore
        */          
        private void getDatastoreSizeProvisioned            (String dsName,   PrintWriter out) throws IOException { 
            long start = System.currentTimeMillis(); 
            Datastore ds = (Datastore)getManagedEntityByName(dsName,"Datastore"); 
            Long provSpace; 
            if (ds == null) { 
                long end = System.currentTimeMillis(); 
                System.out.print("No datastore named '" + dsName + "' found\n"); 
                // request took:" + (end-start) + "\n"); 
                provSpace = new Long(0); 
            } else {
                DatastoreSummary dsSum = ds.getSummary(); 
                long total  = dsSum.getCapacity();
                long free   = dsSum.getFreeSpace(); 
                long uncom  = dsSum.getUncommitted(); 
                long temp   = total - free + uncom; 
                provSpace   = temp; 
                if (provSpace == null) { 
                    provSpace = new Long(0); 
                } 
                // System.out.println("store " + dsName +" free: " + freeSpace 
                // + "\n it took " + (System.currentTimeMillis() - start) ); 
            } 
            out.print(provSpace); 
            out.flush(); 
        } 

               /**
        * Returns the uncommitted size of a datastore
        */  
        private void getDatastoreSizeUncommitted            (String dsName,   PrintWriter out) throws IOException { 
            long start = System.currentTimeMillis(); 
            Datastore ds = (Datastore)getManagedEntityByName(dsName,"Datastore"); 
            Long freeSpace; 
            if (ds == null) { 
                long end = System.currentTimeMillis(); 
                System.out.print("No datastore named '" + dsName + "' found\n"); 
                // request took:" + (end-start) + "\n"); 
                freeSpace = new Long(0); 
            } else { 
                DatastoreSummary dsSum = ds.getSummary(); 
                freeSpace = dsSum.getUncommitted(); 
                if (freeSpace == null) { 
                    freeSpace = new Long(0); 
                } 
                // System.out.println("store " + dsName +" free: " + freeSpace 
                // + "\n it took " + (System.currentTimeMillis() - start) ); 
            } 
            out.print(freeSpace); 
            out.flush(); 
        }          

        public void run (){
            System.out.println("thread created, collecting data in " + (Thread.activeCount() - 1) + " threads" );
            int reincornate = 1;
            final int lifeTime = 2000;
            int alive = 0;
            while (reincornate == 1){
                Request request = VmBix.pullConnection();
                if (request == null) {
                    VmBix.sleep(10);
                    alive += 10;
                    // System.out.println("alive=" + alive);
                } else {
                    connected       = request.socket;
                    serviceInstance = request.serviceInstance;
                    alive = 0;
                    try {
                        // System.out.println("thread loop started");
                        PrintWriter out = new PrintWriter(connected.getOutputStream());
                        BufferedReader in = new BufferedReader(new InputStreamReader(connected.getInputStream()));
                        int continues = 1;
                        while (continues == 1){
                            String message = in.readLine();
                            //System.out.println("Message : " + message);

                            checkAllPatterns(message, out);
                            continues = 0;

                            // if (message.equals("quit")){
                            //     out.print("Ttyl, " + connected.getInetAddress().getHostAddress() + "\n");
                            //     out.flush();
                            //     System.out.println("Ttyl, " + connected.getInetAddress().getHostAddress() + "\n");
                            //     continues = 0;
                            // }
                            // if (message.equals("total quit")){
                            //     out.print("total ttyl, " + connected.getInetAddress().getHostAddress() + "\n");
                            //     out.flush();
                            //     System.out.println("total ttyl, " + connected.getInetAddress().getHostAddress() + "\n");
                            //     
                            //     System.exit(1);
                            // }

                        }
                        in.close();
                        out.close();
                        connected.close();
                        // System.out.println("thread loop finished, going to sleep");
                    }
                    catch (IOException e){
                        System.out.println("thread I/O error: "
                            + e.toString() + "\n closing socket"
                            );
                            try {
                                connected.close();
                            }
                            catch (IOException ee){
                                System.out.println("thread I/O error, can't close socket: "
                                    + ee.toString()
                                    );
                            }
                    }
                }
                if (alive > lifeTime) {
                    System.out.println("thread  closed, collecting data in " + (Thread.activeCount() - 2) + " threads");
                    reincornate = 0;
                }
            }
        }
        
    }

    static class Shutdown extends Thread {
        public void run() {
            System.out.println("Shutting down");
            VmBix.shutdown();
        }
    }

}
