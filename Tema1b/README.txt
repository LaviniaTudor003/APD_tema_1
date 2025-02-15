Pentru prima implementare, cea cu prioritizarea cititorilor:
- cititorii au putut accesa resursa in paralel in absenta scriitorilor, astfel se minimizeaza blocajele pentru un numar mare de cititori.
- o problema pe care am intampinat-o initial a fost legata de secventele generate si de numarul total de scrieri, fiind o baza de date bazata pe index, am vrut ca fiecare thread sa isi vada de bucatica lui.
- de la asta a venit ideea de paralelizare, implementarea thread pool ului propriu a fost ceea ce a facilitat tot demersul(nu doar pentru aceasta varianta, ci per total):
  - a eliminat overhead-ul costisitor pentru crearea si distrugerea firelor la fiecare task nou
  - taskurile sunt rulate in paralel, astfel avand parte de o preluare rapida a taskurilor

Pentru a doua implementare cu prioritizarea scrierii:
- scriitorii blocheaza accesul cititorilor ceea ce duce la intarzieri cu atat mai mult cu cat in unele teste avem taskuri de 500 si latente ridicate (150 - 300ms).
- aici a trebuit sa asigur consistenta datelor, deoarece scrierile au fost realizate fara interferente
- pentru workload uri in care avem mai multi cititori apar latente, astfel concurenta a fost cumva redusa


Pentru a treia implementare cu prioritizarea scrierii:
- am apelat la folosirea monitoarelor, desi implementarea este asemanatoare cu cea cu semafoare, aici a trebuit gestionat mult mai in detaliu partea de sincronizare.
- avem notifyALL() doar la final si doar daca sunt indeplinite conditiile, moment in care trebuie sa trezim scriitori sau cititorii dupa caz - dar asta a adaugat un overhead fiind nevoie sa trezim mai multe threaduri simultan.
- in acest caz, cititorii nu s-au mai blocat inutil, deci cumva este o varianta mai performanta decat precedenta, fiind si o implementare mai flexibila.

