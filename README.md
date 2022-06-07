# Eisenerz

This repository contains code for an art project.
See [Portfolio](https://www.sciss.de/texts/med_chainreaction.html).

(C)opyright 2016–2022 by Hanns Holger Rutz. All rights reserved. This project is released under the
[GNU General Public License](https://github.com/Sciss/Eisenerz/blob/main/LICENSE) v3+ and
comes with absolutely no warranties.
To contact the author, send an e-mail to `contact at sciss.de`.

## building

Builds with sbt against Scala 2.11 (and some against 2.12). The subprojects are independent sbt projects, found in directories
`accelerate`, `experiments`, `exposure`, `rails`, `zerophase`.

Since the original project happened, some subproject have been lifted to newer Scala versions (compiles, but largely untested):

- `accelerate` : Scala 2.12

