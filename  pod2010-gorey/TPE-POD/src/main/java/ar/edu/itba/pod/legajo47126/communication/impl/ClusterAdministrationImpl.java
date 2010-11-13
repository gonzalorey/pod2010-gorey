package ar.edu.itba.pod.legajo47126.communication.impl;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.log4j.Logger;
import org.joda.time.DateTime;

import ar.edu.itba.pod.legajo47126.communication.impl.message.MessageFactory;
import ar.edu.itba.pod.legajo47126.communication.interfaces.RegistryPort;
import ar.edu.itba.pod.legajo47126.node.CoordinationManager;
import ar.edu.itba.pod.legajo47126.node.Node;
import ar.edu.itba.pod.legajo47126.node.NodeManagement;
import ar.edu.itba.pod.simul.communication.ClusterAdministration;
import ar.edu.itba.pod.simul.communication.Message;

public class ClusterAdministrationImpl implements ClusterAdministration, RegistryPort {

	// instance of the log4j logger
	private static Logger logger = Logger.getLogger(ClusterAdministrationImpl.class);
	
	// destination node to be used during the cluster operations
	private Node destinationNode = null;	// TODO change destination for local in every place in this class...
	
	// name of the group that the node is connected to
	private String groupId = null; 
	
	// collection with the nodes that belong to the group 
	private CopyOnWriteArrayList<String> groupNodes = null;
	
	/**
	 * Instance the cluster administration with the destination node
	 * 
	 * @param destinationNode node to be used as the destination to all the cluster operations
	 * @throws RemoteException 
	 */
	public ClusterAdministrationImpl(Node destinationNode) throws RemoteException{
		UnicastRemoteObject.exportObject(this, 0);
		
		// set the reference to the destination node
		this.destinationNode = destinationNode;	// TODO refactor, erase this and call the NodeManagement.getLocalNode()
		
		// instantiate the list of group nodes with a concurrent array list
		groupNodes = new CopyOnWriteArrayList<String>();
	}
	
	@Override
	public void createGroup() throws RemoteException {
		logger.debug("Creating the group...");
		
		if (groupId == null){
			// set the group id with the current milliseconds
			long millis = new DateTime().getMillis();
			groupId = Long.toString(millis); 
		} else {
			throw new IllegalStateException("The node belongs to a group already");
		}
	}
	
	@Override
	public String getGroupId() throws RemoteException {
		return groupId;
	}
	
	@Override
	public boolean isConnectedToGroup() throws RemoteException {
		return (groupId != null);
	}
		
	@Override
	public void connectToGroup(String initialNode) throws RemoteException {		
		
		logger.debug("Connecting to a group...");
		
		if(groupId != null)
			// the destination node already belongs to a group
			throw new IllegalStateException("The destination node " + destinationNode + 
					" already belongs to a group");
		
		logger.debug("The destination node " + destinationNode + " doesn't belong to a group");
		
		if(initialNode.equals(destinationNode.getNodeId()))
			// the destination node is the same as the initial node
			throw new IllegalArgumentException("The destination node " + destinationNode + 
					" is the same as the initial node");
		
		logger.debug("Get the group id of the initial node " + initialNode);
		
		// set the destination node group as the one from the initial node
		groupId = ConnectionManagerImpl.getInstance().getConnectionManager(initialNode).
				getClusterAdmimnistration().getGroupId();
		
		logger.debug("Destination node " + destinationNode + " connected to " + groupId + ". " +
				"Now, tell the initial node " + initialNode + " to add him");
		
		try{
			// tell the initial node to add the destination node
			Iterable<String> initialNodeGroupNodes = ConnectionManagerImpl.getInstance().getConnectionManager(initialNode).
			getClusterAdmimnistration().addNewNode(destinationNode.getNodeId());
			
			// adding the initial node group nodes to the groupNodes
			for(String nodeId : initialNodeGroupNodes){
				if(!nodeId.equals(destinationNode.getNodeId())){
					groupNodes.add(nodeId);
					logger.debug("Node [" + nodeId + "] added to the group nodes list");
				}
			}
			
			groupNodes.add(initialNode);
			logger.debug("Node [" + initialNode + "] added to the group nodes list");
			
		} catch (Exception e) {
			// set the group id back to the default state
			groupId = null;
			logger.error("There was an error during the addition of the destination node [" + destinationNode + "]");  
			logger.error("Error message:" + e.getMessage());
			e.printStackTrace();
			
			throw new RemoteException();
		}
		
		logger.debug("The initial node " + initialNode + " successfully added the " +
				"destination node " + destinationNode + " to the group");
		
		// broadcast a message saying that the local node is the new coordinator
		logger.debug("Start coordinating, inform all the others");
		Message message = MessageFactory.NodeAgentLoadRequestMessage();
		ConnectionManagerImpl.getInstance().getGroupCommunication().broadcast(message);
		
		// restart the nodeAgentsLoad
		NodeManagement.resetNodeAgentsLoad();
		
		// start the coordinator thread
		CoordinationManager coordinatorManager = new CoordinationManager();
		new Thread(coordinatorManager).start();
	}
	
	@Override
	public Iterable<String> addNewNode(String newNode) throws RemoteException {
		
		logger.debug("Adding a new node...");
		
		if(groupId == null)
			// the destination node is not connected to a cluster
			throw new IllegalStateException("The destination node " + destinationNode + 
					" is not connected to a group");
		
		logger.debug("The destination node " + destinationNode + " is connected to the group " + groupId);
		
		// get the new node group id
		String newNodeGroupId = ConnectionManagerImpl.getInstance().getConnectionManager(newNode).
							getClusterAdmimnistration().getGroupId();
		
		logger.debug("Seeing if the destination node's group is the same as the new node's group");
		
		if(!groupId.equals(newNodeGroupId))
			// the destination node's group id isn't the same as the newNode's group
			throw new IllegalArgumentException("The destination node's group isn't the same as" +
					"the new node's group");
		
		// obtain the random nodes to return
		CopyOnWriteArrayList<String> randomGroupNodes = getRandomGroupNodes(groupNodes);
		
		// add the initial node to other known nodes
		addNewNodeToOtherNodes(newNode);
		
		groupNodes.add(newNode);
		logger.debug("New node " +  newNode + " added successfully to the group");
		
		return randomGroupNodes;
	}

	@Override
	public void disconnectFromGroup(String nodeId) throws RemoteException {
		logger.debug("Disconnecting the node [" +  nodeId + "] from the group");
		
		if(!groupNodes.contains(nodeId))
			throw new IllegalArgumentException("The node doesn't belong to the group");
		
		// remove the node from the group and the known nodes list 
		groupNodes.remove(nodeId);
		ConnectionManagerImpl.getInstance().getKnownNodes().remove(nodeId);
		logger.debug("Node removed from groupNodes and knownNodes lists");

		// create the DISCONNECT message
		Message message = MessageFactory.DisconnectMessage(nodeId);
		logger.debug("Built message [" + message + "], broadcast it");
		
		ConnectionManagerImpl.getInstance().getGroupCommunication().broadcast(message);
	}
	
	private CopyOnWriteArrayList<String> getRandomGroupNodes(CopyOnWriteArrayList<String> groupNodes){
		CopyOnWriteArrayList<String> randomGroupNodes = new CopyOnWriteArrayList<String>();
		
		Random rand = new Random();
		double comparator = rand.nextDouble();
		
		for(String nodeId : groupNodes){
			if(rand.nextDouble() < comparator)
				randomGroupNodes.add(nodeId);
		}
		
		return randomGroupNodes;
	}
	
	public CopyOnWriteArrayList<String> getGroupNodes(){
		return groupNodes;
	}
	
	public void disconnectFromGroupWithoutBroadcasting(String nodeId) throws RemoteException {
		logger.debug("Disconnecting the node [" +  nodeId + "] from the group");
		
		if(!groupNodes.contains(nodeId))
			throw new IllegalArgumentException("The node doesn't belong to the group");
		
		// remove the node from the group and the known nodes list 
		groupNodes.remove(nodeId);
		ConnectionManagerImpl.getInstance().getKnownNodes().remove(nodeId);
		logger.debug("Node removed from groupNodes and knownNodes lists");
	}
	
	private void addNewNodeToOtherNodes(String newNode) {
		logger.debug("Adding node [" +  newNode + "] to other nodes...");
		
		Random rand = new Random();
		double comparator = rand.nextDouble();
		int amountAdded = 0;
		
		for(String nodeId : groupNodes){
			if(rand.nextDouble() < comparator){
				try {
					ConnectionManagerImpl.getInstance().getConnectionManager(nodeId).getClusterAdmimnistration().
						addNewNode(newNode);
					logger.debug("Added to node [" + nodeId + "]");
					amountAdded++;
				} catch (RemoteException e) {
					logger.error("The node [" + newNode + "] couldn't be added to the node [" + nodeId + "]");
					logger.error("Error message:" + e.getMessage());
				}
			}
		}
		
	 	logger.debug("Node added to " + amountAdded + " node" + ((amountAdded != 1)?"s":""));
	}
	
}
