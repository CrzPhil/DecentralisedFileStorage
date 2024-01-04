# Decentralised File Storage

The idea is that we have an unorganised Peer-to-peer (P2P) network of nodes, where a node can upload data, which is split up and distributed to the peers. The data is stored and distributed in a way such that the initial node can request and reassemble it. 

![DFS_sketch](https://github.com/CrzPhil/DecentralisedFileStorage/assets/21006533/e565a444-f487-4441-a15f-7048895be022)

## Protocol (WIP)

In general, instructions are four characters followed by a question mark (`?`) and responses are six characters followed by an exclamation mark (`!`). Some received instructions in some states lead to state changes, but this is too volatile to map out at such an early stage. Message objects, as defined in the current code, allow for data transmission, as some instructions like `TAKE? <int>` expect parameters. 

|       Instruction      | State   | State     | State        | State   |
| ----------- | ------- | --------- | ------------ | ------- |
|  | STANDBY | ACCEPTING | DISTRIBUTING | LOOKING |
| REDY?       | AFFIRM! | NOOOPE!   | NOOOPE!      | NOOOPE! |
| TAKE?       | REJECT! | ACCEPT!   | REJECT!      | REJECT! |
| GIVE?       | AFFIRM! | NOOOPE!   | NOOOPE!      | NOOOPE! |
| QERY?       | REJECT! | REJECT!   | ACCEPT!      | REJECT! |

#### All instructions and responses so that I don't lose track of them:

| Instructions | Responses |
| ------------ | --------- |
| REDY?        | AFFIRM!   |
| TAKE?        | NOOOPE!   |
| GIVE?        | ACCEPT!   |
| QERY?        | REJECT!   |
| DONE?        | THANKS!   |
| ALIVE?       |           |

## Ideas

Part of the idea is that a Node can share its data to a portion of the network (`n` Nodes), which in turn can also splice and share their share of data, should they choose to do so. The data splitting and distribution would be done in a manner such that it cannot be reassembled by any node but the data's owner. 

Another part is an implementation where the data is encoded in a way where some loss of data (i.e. by a node dropping out without sharing its data) is acceptable and not detrimental to the data's integrity. (Hamming codes?)

- Hamming Codes
- Reed-Solomon Codes
- Turbo Codes
- Shor codes (quantum)


