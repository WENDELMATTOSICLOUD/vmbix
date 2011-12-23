/*
# vmware api comunication daemon
# This daemon connects to vCenter with vijava SDK
# and gives access to some statistics over tcp/ip socket
#
# Written by Roman Belyakovsky
#            ihryamzik@gmail.com
#
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

public class VmBix {
    static ArrayList<Socket> sockets;
    static ServiceInstance serviceInstance;
    static InventoryNavigator inventoryNavigator;
    static String  sdkUrl;
    static String  uname;
    static String  passwd;
    static Integer port;
    static String  pidFile;

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
            CmdLineParser.Option oConfig = parser.addStringOption( 'c', "config");
            try {
                parser.parse(args);
            }
            catch ( CmdLineParser.OptionException e ) {
                System.err.println(e.getMessage());
                // printUsage();
                System.exit(2);
            }
            
            sdkUrl  = (String )parser.getOptionValue(oSurl  );
            uname   = (String )parser.getOptionValue(oUname );
            passwd  = (String )parser.getOptionValue(oPasswd);
            port    = (Integer)parser.getOptionValue(oPort  );
            pidFile = (String )parser.getOptionValue(oPid   );
            
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
            "Usage:\nvmbix "
            + sport + " " + ssurl + " " + sname + " " + spass + " [-f|--pid pidfile]" + "\n"
            + "or\nvmbix [-c|--config] config_file  [-f|--pid pidfile]\n"
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
            System.out.println("thread sleep error: " + e.toString() );
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
        static private String checkPattern (Pattern pattern, String string) {
            Matcher matcher = pattern.matcher(string);
            if (matcher.find()){
                return matcher.group(1);
            }
            return null;
        }

        private void checkAllPatterns (String string, PrintWriter out) throws IOException {
            Pattern pStatus                = Pattern.compile("^(?:\\s*ZBXD.)?.*status\\[(.+)\\]"                    );
            Pattern pHostCpuUsed           = Pattern.compile("^(?:\\s*ZBXD.)?.*esx\\.cpu\\.load\\[(.+),used\\]"     );
            Pattern pHostCpuTotal          = Pattern.compile("^(?:\\s*ZBXD.)?.*esx\\.cpu\\.load\\[(.+),total\\]"    );
            Pattern pHostCpuCores          = Pattern.compile("^(?:\\s*ZBXD.)?.*esx\\.cpu\\.load\\[(.+),cores\\]"    );
            Pattern pHostMemUsed           = Pattern.compile("^(?:\\s*ZBXD.)?.*esx\\.memory\\[(.+),used\\]"         );
            Pattern pHostMemTotal          = Pattern.compile("^(?:\\s*ZBXD.)?.*esx\\.memory\\[(.+),total\\]"        );
            Pattern pHostMemStatsActive    = Pattern.compile("^(?:\\s*ZBXD.)?.*esx\\.vms.memory\\[(.+),active\\]"   );
            
            Pattern pVmCpuUsed             = Pattern.compile("^(?:\\s*ZBXD.)?.*vm\\.cpu\\.load\\[(.+),used\\]"      );
            Pattern pVmCpuTotal            = Pattern.compile("^(?:\\s*ZBXD.)?.*vm\\.cpu\\.load\\[(.+),total\\]"     );
            Pattern pVmMemPrivate          = Pattern.compile("^(?:\\s*ZBXD.)?.*vm\\.mem\\[(.+),private\\]"          );
            Pattern pVmMemShared           = Pattern.compile("^(?:\\s*ZBXD.)?.*vm\\.mem\\[(.+),shared\\]"           );
            Pattern pVmMemSwapped          = Pattern.compile("^(?:\\s*ZBXD.)?.*vm\\.mem\\[(.+),swapped\\]"          );
            Pattern pVmMemCompressed       = Pattern.compile("^(?:\\s*ZBXD.)?.*vm\\.mem\\[(.+),compressed\\]"       );
            Pattern pVmMemOverheadConsumed = Pattern.compile("^(?:\\s*ZBXD.)?.*vm\\.mem\\[(.+),overheadConsumed\\]" );
            Pattern pVmMemConsumed         = Pattern.compile("^(?:\\s*ZBXD.)?.*vm\\.mem\\[(.+),consumed\\]"         );
            Pattern pVmMemBalooned         = Pattern.compile("^(?:\\s*ZBXD.)?.*vm\\.mem\\[(.+),balooned\\]"         );
            Pattern pVmMemActive           = Pattern.compile("^(?:\\s*ZBXD.)?.*vm\\.mem\\[(.+),active\\]"           );
            Pattern pVmMemSize             = Pattern.compile("^(?:\\s*ZBXD.)?.*vm\\.mem\\[(.+),total\\]"            );
            Pattern pDatastoreFree         = Pattern.compile("^(?:\\s*ZBXD.)?.*datastore\\.size\\[(.+),free\\]"     );
            Pattern pDatastoreTotal        = Pattern.compile("^(?:\\s*ZBXD.)?.*datastore\\.size\\[(.+),total\\]"    );

            String found;
            found = checkPattern(pStatus                ,string); if (found != null) { getHostStatus            (found, out); return; }
            found = checkPattern(pHostCpuUsed           ,string); if (found != null) { getHostCpuUsed           (found, out); return; }
            found = checkPattern(pHostCpuTotal          ,string); if (found != null) { getHostCpuTotal          (found, out); return; }
            found = checkPattern(pHostMemUsed           ,string); if (found != null) { getHostMemUsed           (found, out); return; }
            found = checkPattern(pHostMemTotal          ,string); if (found != null) { getHostMemTotal          (found, out); return; }
            
            found = checkPattern(pHostMemStatsActive    ,string); if (found != null) { getHostVmsStatsActive    (found, out); return; }
            
            found = checkPattern(pVmCpuUsed             ,string); if (found != null) { getVmCpuUsed             (found, out); return; }
            found = checkPattern(pVmCpuTotal            ,string); if (found != null) { getVmCpuTotal            (found, out); return; }
            found = checkPattern(pVmMemPrivate          ,string); if (found != null) { getVmMemPrivate          (found, out); return; }
            found = checkPattern(pVmMemShared           ,string); if (found != null) { getVmMemShared           (found, out); return; }
            found = checkPattern(pVmMemSwapped          ,string); if (found != null) { getVmMemSwapped          (found, out); return; }
            found = checkPattern(pVmMemCompressed       ,string); if (found != null) { getVmMemCompressed       (found, out); return; }
            found = checkPattern(pVmMemOverheadConsumed ,string); if (found != null) { getVmMemOverheadConsumed (found, out); return; }
            found = checkPattern(pVmMemConsumed         ,string); if (found != null) { getVmMemConsumed         (found, out); return; }
            found = checkPattern(pVmMemBalooned         ,string); if (found != null) { getVmMemBalooned         (found, out); return; }
            found = checkPattern(pVmMemActive           ,string); if (found != null) { getVmMemActive           (found, out); return; }
            found = checkPattern(pVmMemSize             ,string); if (found != null) { getVmMemSize             (found, out); return; }
            found = checkPattern(pHostCpuCores          ,string); if (found != null) { getHostCpuCores          (found, out); return; }
            found = checkPattern(pDatastoreFree         ,string); if (found != null) { getDatastoreSizeFree     (found, out); return; }
            found = checkPattern(pDatastoreTotal        ,string); if (found != null) { getDatastoreSizeTotal    (found, out); return; }
            
            System.out.println("String '" + string + "' not supported");
            out.print("ZBX_NOTSUPPORTED\n");
            out.flush();
        }
        
        private Boolean reconnectRequred (ManagedEntity me) throws IOException {
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
                // else {
                //     System.out.println("At least one host found, connection seems to be fine");
                // }
            }
            return required;
        }
        
        private ManagedEntity getManagedEntityByName (String vmName, String meType) throws IOException {
            ManagedEntity me = inventoryNavigator.searchManagedEntity(meType, vmName);
            if (reconnectRequred(me)){
                me = inventoryNavigator.searchManagedEntity(meType, vmName);
            }
            return me;
        }

        private Integer getHostMHZ            (HostSystem host) throws IOException {
            HostListSummary hls = host.getSummary();
            HostHardwareSummary hosthwi = hls.getHardware();
            Integer mhz = hosthwi.getCpuMhz();
            if (mhz == null) {
                mhz = 0;
            }
            return mhz;
        }
        
        private void getHostStatus            (String hostName, PrintWriter out) throws IOException {
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
                // System.out.println("host status: " + hscs + " = " + intStatus
                //                + "\nrequest took:" + (end-start));
            }
            out.print(intStatus + "\n");
            out.flush();
        }
        
        private void getHostCpuUsed           (String hostName, PrintWriter out) throws IOException {
            long start = System.currentTimeMillis();
            HostSystem host = (HostSystem)getManagedEntityByName(hostName,"HostSystem");
            Integer usedMhz;
            if (host == null) {
                long end = System.currentTimeMillis();
                System.out.print("No host named '" + hostName + "' found\n");
                // request took:" + (end-start) + "\n");
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
            out.print(usedMhz + "\n");
            out.flush();
        }
        
        private void getHostCpuTotal          (String hostName, PrintWriter out) throws IOException {
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
            out.print(totalMhz + "\n");
            out.flush();
        }
        
        private void getHostCpuCores          (String hostName, PrintWriter out) throws IOException {
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
            out.print(cores + "\n");
            out.flush();
        }
        
        private void getHostMemUsed           (String hostName, PrintWriter out) throws IOException {
            long start = System.currentTimeMillis();
            HostSystem host = (HostSystem)getManagedEntityByName(hostName,"HostSystem");
            Integer usedMhz;
            if (host == null) {
                long end = System.currentTimeMillis();
                System.out.print("No host named '" + hostName + "' found\n");
                // request took:" + (end-start) + "\n");
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
                // System.out.println("host " + hostName + " memory used: " + usedMhz
                // + "\nrequest took:" + (end-start));
            }
            out.print(usedMhz + "\n");
            out.flush();
        }
        
        private void getHostMemTotal          (String hostName, PrintWriter out) throws IOException {
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
            out.print(totalMemBytes + "\n");
            out.flush();
        }

        private void getHostVmsStatsActive    (String hostName,   PrintWriter out) throws IOException {
            long start = System.currentTimeMillis();
            HostSystem host = (HostSystem)getManagedEntityByName(hostName,"HostSystem");
            Integer amount;
            int sum = 0;
            int activeVms = 0;
            if (host == null) {
                long end = System.currentTimeMillis();
                System.out.print("No host named '" + hostName + "' found\n");
                // request took:" + (end-start) + "\n");
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
                // System.out.println("average = " + sum/activeVms);
                amount = sum/activeVms;
            }
            out.print(amount + "\n");
            out.flush();
        }

        private void getVmCpuUsed             (String vmName,   PrintWriter out) throws IOException {
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

            out.print(usedMhz + "\n");
            out.flush();
        }
        
        private void getVmCpuTotal            (String vmName,   PrintWriter out) throws IOException {
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
            out.print(mhz + "\n");
            out.flush();
        }
        
        private void getVmMemPrivate          (String vmName,   PrintWriter out) throws IOException {
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
            out.print(amount + "\n");
            out.flush();
        }

        private void getVmMemShared           (String vmName,   PrintWriter out) throws IOException {
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
            out.print(amount + "\n");
            out.flush();
        }

        private void getVmMemSwapped          (String vmName,   PrintWriter out) throws IOException {
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
            out.print(amount + "\n");
            out.flush();
        }

        private void getVmMemCompressed       (String vmName,   PrintWriter out) throws IOException {
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
            out.print(amount + "\n");
            out.flush();
        }

        private void getVmMemOverheadConsumed (String vmName,   PrintWriter out) throws IOException {
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
            out.print(amount + "\n");
            out.flush();
        }

        private void getVmMemConsumed         (String vmName,   PrintWriter out) throws IOException {
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
            out.print(amount + "\n");
            out.flush();
        }

        private void getVmMemBalooned         (String vmName,   PrintWriter out) throws IOException {
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
            out.print(amount + "\n");
            out.flush();
        }

        private void getVmMemActive           (String vmName,   PrintWriter out) throws IOException {
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
            out.print(amount + "\n");
            out.flush();
        }

        private void getVmMemSize             (String vmName,   PrintWriter out) throws IOException {
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
            out.print(amount + "\n");
            out.flush();
        }
        
        private void getDatastoreSizeFree     (String dsName,   PrintWriter out) throws IOException {
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
                freeSpace = dsSum.getFreeSpace();
                if (freeSpace == null) {
                    freeSpace = new Long(0);
                }
                // System.out.println("store " + dsName +" free: " + freeSpace
                // + "\n it took " + (System.currentTimeMillis() - start) );
            }
            out.print(freeSpace + "\n");
            out.flush();
        }

        private void getDatastoreSizeTotal    (String dsName,   PrintWriter out) throws IOException {
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
            out.print(capacity + "\n");
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
                            // System.out.println(message);

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
