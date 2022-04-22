# SubPooling
SubPooling is the concept of creating pools of miners devoting their hashrates to specific groups, causes, or simply
among themselves as friends. SubPooling enhances decentralization of GetBlok's Pool Platform by ensuring a strict division
of privileges and jobs for each actor within the system. SubPooling also brings the power of smart contract based mining pools
to any user, allowing for Proof-of-Vote based miner voting, token mining, donation of mining rewards, and even routing
rewards to potentially any other contract in the Ergo Ecosystem. Most of all SubPooling serves as the base layer for
many other future projects to be built off of, some of which will further decentralize and secure the current
Smart Pool framework.

## Terminology
There are a few important terms that will be used throughout the SubPooling contracts:

**Share Operator** - In this case, GetBlok itself. The Share Operator provides a service to manage the automatic sending of
transactions, along with the collection of shares into a *centralized* database. Although the database used is centralized, 
the format of the transactions being used ensure transparency. For user-made SubPools, the Share Operator will
only provide shares. All other available options for a pool cannot be controlled by the Share Operator due to
the format of the smart contracts being used. The Share Operator charges a base fee from all pools, but may
not modify any fees set by the pools themselves.

**Pool Operator** - The Pool Operator is the owner / admin of the smart contract mining pool. It should be
relatively trivial and easy to become a Pool Operator, so that any single person may create their own pool.
Pool Operators control all options and features of the pool they manage. This includes payment options,
currencies used (ERG vs tokens), and fees that may or may not be taken.

**Miner** - The miner is simply a miner to a SubPool. Switching between pools must once again be trivial and
easy to do, so that miners do not feel particularly stuck in one pool. 

**Smart Pool** - A set of SubPools managed by a Pool Operator.

**Sub Pool** - A singular SubPool within the Smart Pool, each SubPool is represented by a 
certain box on the blockchain. SubPools are distinguished from another by their *Pool Tag* and *Subpool Id*

**Pool Tag** - A Pool Tag, or Pool Token, is a limited set of tokens whos entire supply is individually placed
into different SubPools at the time of creation. This allows one to distinguish between different Pools
under the entire system.

**SubPool Id** - A numbered identifier that allows one to distinguish between different SubPools within a single
Smart Pool.

**Transaction Group** - A group of transactions that are chained together to provide more throughput. In
general this was a framework designed for SubPooling, but may theoretically be used anywhere large
sets of interrelated boxes must be spent and chained into subsequent transactions.

## Contracts
Many of the contracts used in SubPooling are the same contracts from the original Smart Pool, with a few
modifications to ensure better transaction costing. The general modular framework used is the same, though
user-made SubPools will have a slightly different set of transactions to ensure the Share Operator only
has control over the shares distributed to miners.

### Metadata Contract
In general, this contract is unchanged, with some inefficiencies removed. This metadata contract simply acts
as a way to save the state of the SubPool between distributions. More over, boxes under this contract
hold the Pool Token, and therefore provide a representation for the SubPool on the blockchain.

### Command Contract
This may essentially be any contract. A box under a command contract must hold the registers needed for the
next Metadata Contract to be spent. The lack of specification for this contract ensures that the entire
system is flexible, while still having its key components (transparency and security of payments) secured
by the other contracts in the system.

### Holding Contract
A contract that holds mining rewards, and ensures that their distribution is done according to the shares
provided in R4 of the command contract. The holding contract itself is modular, and different contracts
may be used for different purposes (For example, there are holding contracts for ERG and token mining).

### Emissions Contract
An emissions contract is a new feature. In general, emissions contracts hold some set of tokens
that are then distributed to holding contracts according to some target block reward. Depending on the contract,
block rewards may be static, adjusted by difficulty, adjusted by trading price on a DEX, or adjusted manually.
By having tokens in an emissions contract, it is ensured that tokens never reach the hands of the Share Operator.
This creates a pipeline of transactions and contracts that ensure the security of token based rewards.

# Frameworks / Abstractions
SubPooling deals with large sets of transactions, that must maximize throughput, while minimizing
transaction cost and fees. Each Pool may have up to 100 SubPools, holding a max of 1000 miners. With a 
potentially huge number of Pools possible once user-made SubPools are created, these transactions will
add up quickly. It is therefore inviable to simply send transactions one-by-one while waiting for blocks to confirm.

The general framework used to execute SubPool transactions is called the *Transaction Group* framework.
It was specifically designed for SubPool transactions, but may theoretically be used for any large 
set of transactions that are interrelated.

## Transaction Group
A Transaction Group is simply a set of transactions pertaining to some smart contract system that must
be chained together (using the outputs of one as inputs for another). Transaction Groups deal with some
*selected* set of boxes. We may divide transaction groups into two fundamental components:
- Stages
- Chains

Each component itself is a transaction or set of transactions, and we may therefore treat them similarly to
transactions themselves. Group Components have inputs and outputs, and on a larger scale, the Transaction
Group itself has its own inputs and outputs.
### Stages
Stages represent a singular transaction within the Transaction Group, that provides outputs for **all**
boxes within the selection set. Stages allow for a single transaction, which reduces fees and increases
the speed of the entire Transaction Group. However, Stages are inherently a bottleneck, if one input box
in the Stage fails to evaluate correctly (For example, maybe a singular box's size is over the 4096 byte limit)
then the entire Stage fails and the Transaction Group will fail to evaluate.

A special type of Stage is the **Root Stage**. The Root Stage is the first transaction to be executed within
the entire Transaction Group. It is extremely important, as the Root is where an unordered set of input boxes
with any size may be used to create an ordered set of outputs that will be used throughout the entire Transaction
Group. An important part of the Root Stage is to pre-calculate the exact transaction fees necessary throughout
the entire Transaction Group. We may therefore think of the Root Stage as a way to gather the "fuel" for
the subsequent transactions that will take place. Through this method, it is trivial to avoid problems
such as unexpected selected boxes, inability to pay transaction fees, and double spending of boxes that
were used earlier in the transaction chain.
### Chains
Chains represent transactions that use specific boxes from the selection set, that are then individually
chained from prior Group Components into separate transactions for each selected box. While a Stage
will provide a singular transaction for all selected boxes, Chains represent a set of transactions whose
state is localized to singular boxes within the selection set. Chains are safer to execute, if one Chain fails
to execute due to some issues, then all other Chains in the set should be unaffected. Due to the nature
of being a set of transactions, Chains cost more to execute in terms of transaction fees.

## Other Components
While Transaction Groups themselves are the most important part of the entire system, there are other
abstractions which are used to help service the Transaction Group and ensure that its execution is correct.

### Selectors
Selectors take some data, be it on-chain or off-chain, and create the selection set of boxes which is to be used
and referred to throughout the entire Transaction Group.

### Builders
Builders once again take on-chain and off-chain data, but this component is specifically run after
selection has been completed. Builders first apply modifications and attach off-chain data to each box
in the selection set. This data is then used later in the Transaction Group so that it may find its
way on-chain.

Builders are also where the Root Stage is first executed, and therefore ensure the viability of the entire
Transaction Group following the building process.

### Managers
The Group Manager is simply an object which manages the entire Group and other components and ensures that no issues
arise during the process. Managers catch exceptions thrown by the various components in the Group,
along with handling the general flow of actions from selection, to building, to execution of the entire
Group. Managers will stop all execution in the case of failure for Selectors, Builders, and Stages.
Managers will gracefully handle failures for Chains so that the failure of one Chain does not impact
the execution of others. 

Managers also manage the changing of the selection set after selection has been completed. This ensures that
boxes used in failed Chains are not used in subsequent parts of the Transaction Group. Finally,
the Group Manager holds a queue of all successful transactions that were compiled throughout the
entire execution of the Group. So long as the only failures in the Group occurred in Chains, the
Group Manager will send successful transactions to the node on a FIFO basis.

## Group Parallelization
In order to maximize throughput even further, Groups may be parallelized through multi-threaded processes.
To achieve this, the Input Boxes used during each Group's Root Stage must be pulled from a thread-safe
collection that is pre-loaded with the set of boxes needed. In SubPooling, we use a `ConcurrentLinkedQueue`
that is loaded with boxes at the beginning of the program. Groups are then executed on multiple threads
and assigned boxes from the Queue in order to prevent double spending errors. This allows for a potentially
large set of Groups to be executed at the exact same time while improving efficiency of the entire
program. 
