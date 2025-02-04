package com.master_thesis.verifier;

import ch.qos.logback.classic.Logger;
import com.master_thesis.verifier.data.*;
import com.master_thesis.verifier.utils.PublicParameters;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SpringBootApplication
@RestController
@RequestMapping(value = "/api")
public class VerifierApplication {

    private static final Logger log = (Logger) LoggerFactory.getLogger(VerifierApplication.class);
    private DataBuffer serverBuffer;
    private DataBuffer clientBuffer;
    private Lock bufferLock;
    private RSAThreshold rsaThresholdVerifier;
    private HomomorphicHash homomorphicHashVerifier;
    private LinearSignature linearSignature;
    private DifferentialPrivacy differentialPrivacy;
    private PublicParameters publicParameters;

    @Autowired
    public VerifierApplication(RSAThreshold rsaThresholdVerifier, @Qualifier("homomorphicHash") HomomorphicHash homomorphicHashVerifier, LinearSignature linearSignature, DifferentialPrivacy differentialPrivacy, PublicParameters publicParameters) {
        this.differentialPrivacy = differentialPrivacy;
        this.serverBuffer = new DataBuffer();
        this.clientBuffer = new DataBuffer();
        this.bufferLock = new ReentrantLock();
        this.rsaThresholdVerifier = rsaThresholdVerifier;
        this.homomorphicHashVerifier = homomorphicHashVerifier;
        this.linearSignature = linearSignature;
        this.publicParameters = publicParameters;
    }

    /****
     * START OF API END POINTS
     ****/

    @PostMapping(value = "/server/hash-data")
    public void receiveHashServerData(@RequestBody HashServerData serverData) throws InterruptedException {
        boolean isAllDataAvailable = putData(serverData, serverBuffer);
        if (isAllDataAvailable)
            new Thread(() -> performComputations(serverData.getSubstationID(), serverData.getFid())).start();
    }

    @PostMapping(value = "/client/hash-data")
    public void receiveHashClientData(@RequestBody HashClientData clientData) throws InterruptedException {
        boolean isAllDataAvailable = putData(clientData, clientBuffer);
        if (isAllDataAvailable)
            new Thread(() -> performComputations(clientData.getSubstationID(), clientData.getFid())).start();
    }

    @PostMapping(value = "/server/rsa-data")
    public void receiveRSAServerData(@RequestBody RSAServerData serverData) throws InterruptedException {
        boolean isAllDataAvailable = putData(serverData, serverBuffer);
        if (isAllDataAvailable)
            new Thread(() -> performComputations(serverData.getSubstationID(), serverData.getFid())).start();
    }

    @PostMapping(value = "/client/rsa-data")
    public void receiveRSAClientData(@RequestBody RSAClientData clientData) throws InterruptedException {
        boolean isAllDataAvailable = putData(clientData, clientBuffer);
        if (isAllDataAvailable)
            new Thread(() -> performComputations(clientData.getSubstationID(), clientData.getFid())).start();
    }

    @PostMapping(value = "/server/linear-data")
    public void receiveLinearServerData(@RequestBody LinearServerData serverData) throws InterruptedException {
        boolean isAllDataAvailable = putData(serverData, serverBuffer);
        if (isAllDataAvailable)
            new Thread(() -> performComputations(serverData.getSubstationID(), serverData.getFid())).start();
    }

    @PostMapping(value = "/client/linear-data")
    public void receiveLinearClientData(@RequestBody LinearClientData clientData) throws InterruptedException {
        boolean isAllDataAvailable = putData(clientData, clientBuffer);
        if (isAllDataAvailable)
            new Thread(() -> performComputations(clientData.getSubstationID(), clientData.getFid())).start();
    }


    @PostMapping(value = "/server/dp-data")
    public void receiveHashServerData(@RequestBody DPServerData serverData) throws InterruptedException {
        boolean isAllDataAvailable = putData(serverData, serverBuffer);
        if (isAllDataAvailable)
            new Thread(() -> performComputations(serverData.getSubstationID(), serverData.getFid())).start();
    }

    @PostMapping(value = "/client/dp-data")
    public void receiveHashClientData(@RequestBody DPClientData clientData) throws InterruptedException {
        boolean isAllDataAvailable = putData(clientData, clientBuffer);
        if (isAllDataAvailable)
            new Thread(() -> performComputations(clientData.getSubstationID(), clientData.getFid())).start();
    }


    /****
     * END OF API END POINTS
     ****/

    /**
     * Adds the incoming data in the correct buffer
     * @param data to add
     * @param buffer is the place to put the data
     * @return true if the buffers are full and can verify the fid and substation computation
     * @throws InterruptedException
     */
    private boolean putData(ComputationData data, DataBuffer buffer) throws InterruptedException {
        boolean isUnlocked = bufferLock.tryLock(1, TimeUnit.SECONDS);
        boolean canCompute = false;
        if (isUnlocked) {
            try {
                log.debug("Got {}", data);
                buffer.put(data);
                canCompute = canCompute(data.getSubstationID(), data.getFid());
            } finally {
                bufferLock.unlock();
            }
        }
        return canCompute;
    }

    /**
     * Check if the buffers contain the required information to compute the verification
     * @param substationID an identifier for the substation in use
     * @param fid and identifier for the computation
     * @return true: if all clients and servers have sent there information and is put into the buffers
     */
    private boolean canCompute(int substationID, int fid) {
        List<Integer> servers = publicParameters.getServers(); // TODO: 16/04/2020 Add substationID, fid
        List<Integer> clients = publicParameters.getClients(substationID, fid);
        if (serverBuffer.contains(substationID, fid) && clientBuffer.contains(substationID, fid)) {
            boolean serverDataAvailable = serverBuffer.getFid(substationID, fid).keySet().containsAll(servers);
            boolean clientDataAvailable = clientBuffer.getFid(substationID, fid).keySet().containsAll(clients);
            return serverDataAvailable && clientDataAvailable;
        }
        return false;
    }


    /**
     * This function is internal and map the construction in use to the correct function
     * @param substationID an identifier for the substation in use
     * @param fid and identifier for the computation
     */
    private void performComputations(int substationID, int fid) {

        DataBuffer.Fid bufferServerData = serverBuffer.getFid(substationID, fid);
        DataBuffer.Fid bufferClientData = clientBuffer.getFid(substationID, fid);

        log.info("### Perform computation fid: {} Substation: {} Construction {}", fid, substationID, bufferServerData.getConstruction());

        // Homomorphic Hash verification
        if (bufferServerData.getConstruction().equals(Construction.HASH)) {
            List<HashServerData> serverData = bufferServerData.values().stream().map(val -> (HashServerData) val).collect(Collectors.toList());
            List<HashClientData> clientData = bufferClientData.values().stream().map(val -> (HashClientData) val).collect(Collectors.toList());
            performHomomorphicHashComputation(serverData, clientData, substationID, fid);
        }

        // RSA verification
        if (bufferServerData.getConstruction().equals(Construction.RSA)) {
            List<RSAServerData> serverData = bufferServerData.values().stream().map(val -> (RSAServerData) val).collect(Collectors.toList());
            List<RSAClientData> clientData = bufferClientData.values().stream().map(val -> (RSAClientData) val).collect(Collectors.toList());
            performRSAThresholdComputation(serverData, clientData, substationID, fid);
        }

        // Linear verification
        if (bufferServerData.getConstruction().equals(Construction.LINEAR)) {
            List<LinearServerData> serverData = bufferServerData.values().stream().map(val -> (LinearServerData) val).collect(Collectors.toList());
            List<LinearClientData> clientData = bufferClientData.values().stream().map(val -> (LinearClientData) val).collect(Collectors.toList());
            performLinearSignatureComputation(serverData, clientData, substationID, fid);
        }

        // Differential Privacy verification
        if (bufferServerData.getConstruction().equals(Construction.DP)) {
            List<DPServerData> serverData = bufferServerData.values().stream().map(val -> (DPServerData) val).collect(Collectors.toList());
            List<DPClientData> clientData = bufferClientData.values().stream().map(val -> (DPClientData) val).collect(Collectors.toList());
            performDifferentialPrivacyComputation(serverData, clientData, substationID, fid);
        }

    }

    /**
     * This function handles all the computations for the Homomorphic Hash based construction
     * @param serverData a list of all the data from the servers for the given fid and substation
     * @param clientData a list of all the data from the clients for the given fid and substation
     * @param substationID id for the substation
     * @param fid id for the computation
     */
    private void performHomomorphicHashComputation(List<HashServerData> serverData, List<HashClientData> clientData, int substationID, int fid) {
//        Collect the clients' proof (tau) from the data object
        List<BigInteger> clientProofs = clientData.stream().map(HashClientData::getProofComponent).collect(Collectors.toList());
//        Query the trusted third-party to receive Rn and add it to the list of client proofs
        BigInteger lastClientProof = publicParameters.getLastClientProof(substationID, fid);
        clientProofs.add(lastClientProof);
//        Compute the final evaluation, i.e., compute the final sum of the servers partial sum
        BigInteger hashResult = homomorphicHashVerifier.finalEval(serverData.stream().map(HashServerData::getPartialResult));
//        Compute the final proof, i.e., compute the product of the servers' partial proofs
        BigInteger hashServerProof = homomorphicHashVerifier.finalProof(serverData.stream().map(HashServerData::getPartialProof), substationID);
//        Verify that the computations are correct.
        boolean hashValidResult = homomorphicHashVerifier.verify(substationID, hashResult, hashServerProof, clientProofs);
        log.info("[FID {}] Hash: result:{} server proof:{} valid:{}", fid, hashResult, hashServerProof, hashValidResult);
    }


    /**
     * This function handles all the computations for the RSA threshold based construction
     * @param serverData a list of all the data from the servers for the given fid and substation
     * @param clientData a list of all the data from the clients for the given fid and substation
     * @param substationID id for the substation
     * @param fid id for the computation
     */
    private void performRSAThresholdComputation(List<RSAServerData> serverData, List<RSAClientData> clientData, int substationID, int fid) {
//        Collect the clients' proof (tau) from the data object
        List<BigInteger> clientProofs = clientData.stream().map(RSAClientData::getProofComponent).collect(Collectors.toList());
//        Query the trusted third-party to receive Rn and add it to the list of client proofs
        BigInteger lastClientProof = publicParameters.getLastClientProof(substationID, fid);
        clientProofs.add(lastClientProof);
//        Collect the servers' partial results from the data object
        Stream<BigInteger> partialResults = serverData.stream().map(RSAServerData::getPartialResult);
//        Collect the servers' partial proofs from the data obejct
        Map<Integer, RSAServerData.ProofData> serverProofInfo = serverData.get(0).getPartialProofs();

//        Add the public key to each proof component computation.
        clientData.forEach(client -> serverProofInfo.get(client.getId()).setPublicKey(client.getPublicKey()));

//        Compute the final evaluation, i.e., compute the final sum of the servers partial sum
        BigInteger rsaResult = rsaThresholdVerifier.finalEval(partialResults);
//        Compute the final proof, i.e, the product of all servers' proof to the power of the proofs public key
        BigInteger rsaServerProof = rsaThresholdVerifier.finalProof(serverProofInfo.values(), substationID, lastClientProof);
//        Verify that the computations are correct
        boolean rsaValidResult = rsaThresholdVerifier.verify(substationID, rsaResult, rsaServerProof, clientProofs);

        log.info("[FID {}] RSA: result:{} server proof:{} valid:{}", fid, rsaResult, rsaServerProof, rsaValidResult);
    }

    /**
     * This function handles all the computations for the Linear Signature based construction
     * @param serverData a list of all the data from the servers for the given fid and substation
     * @param clientData a list of all the data from the clients for the given fid and substation
     * @param substationID id for the substation
     * @param fid id for the computation
     */
    private void performLinearSignatureComputation(List<LinearServerData> serverData, List<LinearClientData> clientData, int substationID, int fid) {
//        Compute the final evaluation, i.e., compute the final sum of the servers partial sum
        BigInteger linearResult = linearSignature.finalEval(serverData.stream().map(LinearServerData::getPartialResult));
//        Collects the public available data
        LinearPublicData publicData = publicParameters.getLinearPublicData(substationID, fid);
//        Query the trusted third-party to receive Rn
        BigInteger rn = publicParameters.getRn(substationID, fid);
//        Computes the final proof
        LinearProofData proofData = linearSignature.finalProof(clientData, publicData);
//        Verify that the computations are correct
        boolean validResult = linearSignature.verify(linearResult, proofData, publicData, rn);
        log.info("[FID {}] Linear: result:{} valid:{}", fid, linearResult, validResult);
    }

    /**
     * This function handles all the computations for the Differential Privacy based construction
     * @param serverData a list of all the data from the servers for the given fid and substation
     * @param clientData a list of all the data from the clients for the given fid and substation
     * @param substationID id for the substation
     * @param fid id for the computation
     */
    private void performDifferentialPrivacyComputation(List<DPServerData> serverData, List<DPClientData> clientData, int substationID, int fid) {
//        Collect the clients' proof (tau) from the data object
        List<BigInteger> clientProofs = clientData.stream().map(DPClientData::getProofComponent).collect(Collectors.toList());
//        Query the trusted third-party to receive Rn and add it to the list of client proofs
        BigInteger lastClientProof = publicParameters.getLastClientProof(substationID, fid);
        clientProofs.add(lastClientProof);
//        Compute the final evaluation, i.e., compute the final sum of the servers partial sum
        BigInteger DPResult = differentialPrivacy.finalEval(serverData.stream().map(DPServerData::getPartialResult));
//        Compute the final proof, i.e., compute the product of the servers' partial proofs
        BigInteger DPServerProof = differentialPrivacy.finalProof(serverData.stream().map(DPServerData::getPartialProof), substationID);
//        Verify that the computations are correct.
        boolean DPValidResult = differentialPrivacy.verify(substationID, DPResult, DPServerProof, clientProofs);
        log.info("[FID {}] DP: result:{} server proof:{} valid:{}", fid, DPResult, DPServerProof, DPValidResult);
    }


    public static void main(String[] args) {
        SpringApplication.run(VerifierApplication.class, args);
    }

}
