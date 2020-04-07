package com.master_thesis.verifier;

import com.codepoetics.protonpack.StreamUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.master_thesis.verifier.data.LinearClientData;
import com.master_thesis.verifier.data.LinearProofData;
import com.master_thesis.verifier.data.LinearPublicData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class LinearSignature {
    private static final Logger log = (Logger) LoggerFactory.getLogger(LinearSignature.class);


    public BigInteger finalEval(Stream<BigInteger> partialResultInfo, BigInteger fieldBase) {
        return partialResultInfo
                .reduce(BigInteger.ZERO, BigInteger::add)
                .mod(fieldBase);
    }

    public LinearProofData finalProof(List<LinearClientData> clientData, List<Integer> alphas, LinearPublicData publicData) {
        BigInteger eN = publicData.getN().multiply(publicData.getFidPrime());
        BigInteger nRoof = publicData.getNRoof();

        List<BigInteger> f = alphas.stream().map(x -> BigInteger.valueOf(x).mod(eN)).collect(Collectors.toList());
        List<BigInteger> fPrime = StreamUtils.zip(
                alphas.stream().map(BigInteger::valueOf),
                f.stream(),
                (a, x) -> a.subtract(x).divide(eN)).collect(Collectors.toList());

        BigInteger s = StreamUtils.zip(alphas.stream().map(BigInteger::valueOf), clientData.stream().map(LinearClientData::getsShare),
                BigInteger::multiply).reduce(BigInteger.ZERO, BigInteger::add);
        BigInteger sPrime = s.subtract(s.mod(eN)).divide(eN);
//      We compute xTilde in three steps by computing the numerator, denominator and combining them
        BigInteger numerator = StreamUtils.zip(
                clientData.stream().map(LinearClientData::getX),
                alphas.stream(),
                BigInteger::pow)
                .reduce(BigInteger.ONE, BigInteger::multiply);
        BigInteger denominator = publicData.getG().modPow(sPrime, nRoof)
                .multiply(StreamUtils.zip(Arrays.stream(publicData.getH()), fPrime.stream(),
                        (h, fp) -> h.pow(fp.intValue())).reduce(BigInteger.ONE, BigInteger::multiply))
                .modInverse(nRoof);
//      Note that we use modulo inverse and thus we have to use multiplication and not division
        BigInteger xTilde = numerator.multiply(denominator).mod(nRoof);

        return new LinearProofData(s.mod(eN), xTilde, f);
    }

    public boolean verify(BigInteger linearResult, LinearProofData proofData, LinearPublicData publicData, BigInteger rn) {
        BigInteger eN = publicData.getN().multiply(publicData.getFidPrime());

        boolean inEN = linearResult.compareTo(eN) < 0 && proofData.getS().compareTo(eN) < 0;

        BigInteger nRoof = publicData.getNRoof();
//      Below we compute the lhs and rhs and check their equivalence.
//      lhs (clients' secret and nonce)
        BigInteger lhs = proofData.getXTilde().modPow(eN, nRoof);
//      xTilde contains all nonce from the clients. We include Rn to remove these nonce to receive the correct result
        lhs = lhs.multiply(publicData.getG1().modPow(rn, nRoof)).mod(nRoof);
//      rhs (the sum of clients' secret)
        BigInteger rhs = publicData.getG().modPow(proofData.getS(), nRoof)
                .multiply(StreamUtils.zip(Arrays.stream(publicData.getH()), proofData.getF().stream(),
                        (h, f) -> h.modPow(f, nRoof))
                        .reduce(BigInteger.ONE, BigInteger::multiply))
                .multiply(publicData.getG1().modPow(linearResult, nRoof))
                .mod(nRoof);

        boolean correctResult = lhs.equals(rhs);
        if (!correctResult)
            logError(lhs, rhs, linearResult, proofData, publicData);

        return inEN && correctResult;
    }

    private void logError(BigInteger lhs, BigInteger rhs, BigInteger linearResult, LinearProofData proofData, LinearPublicData publicData) {
        ObjectMapper objectMapper = new ObjectMapper();
        log.error("Could not verify the Linear Signature result");
        log.error("lhs: {} != {} :rhs", lhs, rhs);
        log.error("linearResult: {}", linearResult);
        try {
            log.error("proofData: {}", objectMapper.writeValueAsString(proofData));
            log.error("publicData: {}", objectMapper.writeValueAsString(publicData));
        } catch (JsonProcessingException e) {
            log.error(e.getMessage());
        }
    }
}
