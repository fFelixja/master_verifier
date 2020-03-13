package com.master_thesis.verifier;

import ch.qos.logback.classic.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;

import static java.math.BigInteger.ONE;
import static java.math.BigInteger.ZERO;

@Component
public class RSAThreshold extends HomomorphicHash {

    private final static Logger log = (Logger) LoggerFactory.getLogger(RSAThreshold.class);

    @Autowired
    public RSAThreshold(PublicParameters publicParameters) {
        super(publicParameters);
    }

    public BigInteger rsaFinalProof(List<ClientInfo> rsaProofComponents, int transformatorID, VerifierBuffer buffer) {
        if (rsaProofComponents.isEmpty())
            return null;
        return rsaProofComponents.stream()
                .map(rsaProofComponent -> {
                    try {
                        BigInteger encryptedRSAProof = clientFinalProof(
                                rsaProofComponent.getPublicKey(),
                                rsaProofComponent.getClientProof(),
                                rsaProofComponent.getRsaProofComponent(),
                                rsaProofComponent.getRsaN(),
                                rsaProofComponent.getRsaDeterminant());
                        BigInteger res = encryptedRSAProof.modPow(rsaProofComponent.getPublicKey(), rsaProofComponent.getRsaN());
                        log.info("ClientProof: {} res: {}", rsaProofComponent.clientProof, res.mod(publicParameters.getFieldBase(transformatorID)));
                        return res;
                    } catch (Exception e) {
                        log.info(e.getMessage());
                        buffer.pop();
                    }
                    return null;
                })
                .reduce(ONE, BigInteger::multiply)
                .mod(publicParameters.getFieldBase(transformatorID));
    }

    private BigInteger clientFinalProof(BigInteger pk, BigInteger clientProof, BigInteger[] serverProofs, BigInteger rsaN, double determinant) {

        BigInteger partial = Arrays.stream(serverProofs).reduce(ONE, BigInteger::multiply).mod(rsaN);

        // Find alpha and beta
        BigInteger det = BigInteger.valueOf(Math.round(determinant));
        BigInteger detSign = det.divide(det.abs());
        BigInteger[] eeaResult = extendedEuclideanAlgorithm(det.multiply(BigInteger.TWO), pk);
        BigInteger alpha = eeaResult[0].multiply(detSign);
        BigInteger beta = eeaResult[1];

        // Compute the clients' rsa proof component
        BigInteger sigmaRoofAlpha = partial.modPow(alpha, rsaN);
        BigInteger tauBeta = clientProof.modPow(beta, rsaN);
        BigInteger iSigma = sigmaRoofAlpha.multiply(tauBeta).mod(rsaN);
        return iSigma;
    }

    /**
     * Returns the Bézout coefficients and gcd.
     * All input will be mapped to positive numbers by absolute value function.
     * The return coefficient can be multiplied by the original sign of that parameter to restore correct sign.
     *
     * @return first is coefficient of a, second is coefficient of b, third is gcd.
     */
    public static BigInteger[] extendedEuclideanAlgorithm(BigInteger a, BigInteger b) {
        BigInteger aPos = a.abs();
        BigInteger bPos = b.abs();
        BigInteger[] s = new BigInteger[]{ZERO, ONE};
        BigInteger[] t = new BigInteger[]{ONE, ZERO};
        BigInteger[] r = new BigInteger[]{bPos.max(aPos), bPos.min(aPos)};

        while (!r[0].equals(ZERO)) {
            BigInteger quotient = r[1].divide(r[0]);
            r = internalEEA(r, quotient);
            s = internalEEA(s, quotient);
            t = internalEEA(t, quotient);
        }

        //output "Bézout coefficients:", (old_s, old_t)
//        System.out.println("Bézout coefficients: " + s[1] + " " + t[1]);
        //output "greatest common divisor:", old_r
        //output "quotients by the gcd:", (t, s)

        if (bPos.max(aPos).equals(bPos)) {
            return new BigInteger[]{s[1], t[1], r[1]};
        } else {
            return new BigInteger[]{t[1], s[1], r[1]};
        }
    }

    private static BigInteger[] internalEEA(BigInteger[] values, BigInteger q) {
        return new BigInteger[]{values[1].subtract(q.multiply(values[0])), values[0]};
    }

}
