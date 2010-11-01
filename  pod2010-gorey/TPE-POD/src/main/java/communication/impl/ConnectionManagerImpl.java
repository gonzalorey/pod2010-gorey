package communication.impl;

import java.net.UnknownHostException;
import java.rmi.AccessException;
import java.rmi.AlreadyBoundException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

import node.Node;
import node.NodeManagement;

import communication.interfaces.RegistryPort;

import ar.edu.itba.pod.simul.communication.ClusterAdministration;
import ar.edu.itba.pod.simul.communication.ClusterCommunication;
import ar.edu.itba.pod.simul.communication.ConnectionManager;
import ar.edu.itba.pod.simul.communication.ReferenceName;
import ar.edu.itba.pod.simul.communication.SimulationCommunication;
import ar.edu.itba.pod.simul.communication.ThreePhaseCommit;
import ar.edu.itba.pod.simul.communication.Transactionable;

public class ConnectionManagerImpl implements ConnectionManager, ReferenceName, RegistryPort {
	
	// singletone instance of the ConnectionManger
	private static ConnectionManagerImpl connectionManager = null;
	
	// RMI Registry
	private Registry registry;
	
	// ClusterAdministration instance to handle the group connections
	private ClusterAdministration clusterAdministration;
	
	private ConnectionManagerImpl(){
		// start the RMI Registry
		registry = startRMIRegistry();
		
		// instance the ClusterAdministration
		clusterAdministration = new ClusterAdministrationImpl(NodeManagement.getLocalNode());
		
		// publish the ConnectionManager
		try {
			registry.bind(CONNECTION_MANAGER_NAME, this);
		} catch (AccessException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (RemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (AlreadyBoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public static synchronized ConnectionManagerImpl getInstance(){
		if(connectionManager == null)
			ConnectionManagerImpl.connectionManager = new ConnectionManagerImpl();
		
		return ConnectionManagerImpl.connectionManager;
	}
	
	@Override
	protected Object clone() throws CloneNotSupportedException {
		// it won't be cloned now either
		throw new CloneNotSupportedException();
	}
	
	@Override
	public int getClusterPort() throws RemoteException {
		// return always the default port number
		return DEFAULT_PORT_NUMBER;
	}
	
	@Override
	public ConnectionManager getConnectionManager(String nodeId) throws RemoteException {
		
		// instance to return
		ConnectionManager connectionManager = null;
		
		// obtain the target node
		Node targetNode;
		try {
			targetNode = Node.parse(nodeId);
		} catch (NumberFormatException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			throw e;
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			//throw e;
			throw new RemoteException();
		}
		
		// connect to the registry of the target node
		Registry registry = LocateRegistry.getRegistry(targetNode.getHostAddress(), targetNode.getPort());
		
		// obtain the reference to his ConnectionManager
		try {
			connectionManager = (ConnectionManager) registry.lookup(CONNECTION_MANAGER_NAME);
		} catch (NotBoundException e) {
			// TODO log this one
			e.printStackTrace();
			//throw e;
			//throw new NoConnectionAvailableException();
			throw new RemoteException();
		}
		
		return connectionManager;
	}
	
	@Override
	public ClusterCommunication getGroupCommunication() throws RemoteException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ClusterAdministration getClusterAdmimnistration() throws RemoteException {
		return clusterAdministration;
	}

	@Override
	public Transactionable getNodeCommunication() throws RemoteException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public SimulationCommunication getSimulationCommunication() throws RemoteException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ThreePhaseCommit getThreePhaseCommit() throws RemoteException {
		// TODO Auto-generated method stub
		return null;
	}

	private Registry startRMIRegistry(){
		// RMI Registry
		Registry registry = null;
		
		try {
			// create the RMI Registry
			registry = LocateRegistry.createRegistry(NodeManagement.getLocalNode().getPort());
			System.out.println("RMI Registry raised successfully");
		} catch (RemoteException e) {
			// aparently the rmiregistry was already instantiated
			e.printStackTrace();
			
			try {
				// try to obtain the already created RMI Registry
				registry = LocateRegistry.getRegistry();
				System.out.println("RMI Registry joined successfully");
			} catch (RemoteException e1) {
				e1.printStackTrace();
			}
		}
		
		return registry;
	}
}
