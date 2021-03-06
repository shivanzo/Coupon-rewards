package com.example.flow;

import co.paralleluniverse.fibers.Suspendable;
import com.example.contract.CouponContract;
import com.example.state.CouponState;
import com.google.common.collect.ImmutableList;
import net.corda.core.contracts.Command;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.flows.*;
import net.corda.core.identity.Party;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;
import net.corda.core.utilities.ProgressTracker;
import org.jetbrains.annotations.NotNull;

public class CouponFlow {

    @InitiatingFlow
    @StartableByRPC
    public static class Initiator extends FlowLogic<SignedTransaction> {

        private final Party amazonParty;
        private int amount;
        private UniqueIdentifier couponId;

        /**
         * This constructor is being called from REST API
         * **/

        public Initiator(Party amazonParty, int amount) {
            this.amazonParty = amazonParty;
            this.amount = amount;
        }

        public Party getAmazonParty() {
            return amazonParty;
        }

        public int getAmount() {
            return amount;
        }

        public UniqueIdentifier getCouponId() {
            return couponId;
        }

        public void setAmount(int amount) {
            this.amount = amount;
        }


        private final ProgressTracker.Step COUPON_GENERATION = new ProgressTracker.Step("Coupon is generated upon the purchased of medicine");
        private final ProgressTracker.Step VERIFYING_COUPON = new ProgressTracker.Step("Verification of coupon once user tries to redeem it on amazon.");
        private final ProgressTracker.Step SIGNING_TRANSACTION = new ProgressTracker.Step("Signing transaction with our private key.");
        private final ProgressTracker.Step GATHERING_SIGS = new ProgressTracker.Step("Gathering the counterparty's signature.") {
            public ProgressTracker childProgressTracker() {
                return CollectSignaturesFlow.Companion.tracker();
            }
        };

        private final ProgressTracker.Step FINALISING_TRANSACTION = new ProgressTracker.Step("Obtaining notary signature and recording transaction.") {
            public ProgressTracker childProgressTracker() {
                return FinalityFlow.Companion.tracker();
            }
        };

        private final ProgressTracker progressTracker = new ProgressTracker(
                COUPON_GENERATION,
                VERIFYING_COUPON,
                SIGNING_TRANSACTION,
                GATHERING_SIGS,
                FINALISING_TRANSACTION
        );

        @Override
        @Suspendable
        public SignedTransaction call() throws FlowException {

            final Party notary = getServiceHub().getNetworkMapCache().getNotaryIdentities().get(0);

            //Stage 1
            progressTracker.setCurrentStep(COUPON_GENERATION);

            //Generate an unsigned transaction
            Party netmedsParty = getServiceHub().getMyInfo().getLegalIdentities().get(0);
            CouponState couponState = new CouponState(netmedsParty, amazonParty, amount, new UniqueIdentifier());
            final Command<CouponContract.Commands.CouponGeneration> couponGenerationCommand = new Command<CouponContract.Commands.CouponGeneration>(new CouponContract.Commands.CouponGeneration(), ImmutableList.of(couponState.getInitiatingParty().getOwningKey(), couponState.getCounterParty().getOwningKey()));

            final TransactionBuilder txBuilder = new TransactionBuilder(notary)
                    .addOutputState(couponState, CouponContract.COUPONGEN_CONTRACT_ID)
                    .addCommand(couponGenerationCommand);

            txBuilder.verify(getServiceHub());

            progressTracker.setCurrentStep(SIGNING_TRANSACTION);



            return null;
        }
    }


    public SignedTransaction call() throws FlowException {

        class SignTxFlow extends SignTransactionFlow {

            @Override
            protected void checkTransaction(@NotNull SignedTransaction stx) throws FlowException {

            }
        }

    }


}
