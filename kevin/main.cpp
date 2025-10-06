#include <bits/stdc++.h>
#include <ext/pb_ds/assoc_container.hpp>

using namespace std;
using namespace __gnu_pbds;

using ll = long long;
using ld = long double;

template<typename T>
using pair2 = pair<T, T>;
using pi = pair2<int>;
using pd = pair2<double>;
using pl = pair2<ll>;
using pc = pair2<char>;
#define mp make_pair
#define F first
#define S second
#define mt make_tuple

template<typename T>
using vec = vector<T>;
template<typename T>
using vvec = vec<vec<T>>;
using vi = vec<int>;
using vb = vec<bool>;
using vl = vec<ll>;
using vd = vec<double>;
using vs = vec<string>;
using vc = vec<char>;
using vvi = vec<vi>;
using vvb = vec<vb>;
using vvl = vec<vl>;
using vvd = vec<vd>;
using vvs = vec<vs>;
using vvc = vec<vc>;
#define sz(x) int((x).size())
#define all(x) x.begin(), x.end()
#define rall(x) x.rbegin(), x.rend()
#define pb push_back
#define eb emplace_back

template<size_t N>
using vbset = vec<bitset<N>>;

#define FOR(i, a, b) for(auto i = (a); i < (b); i++)
#define FORR(i, b, a) for(auto i = (b); i >= (a); i--)
#define FORI(it, a, b) for(auto it = (a); it != (b); it++)
#define TOKENPASTE_(x, y) x##y
#define TOKENPASTE(x, y) TOKENPASTE_(x, y)
#define REP(n) FOR(TOKENPASTE(HIDDEN_LOOP_VARIABLE_, __COUNTER__), 0, n)
#define SB(...) [__VA_ARGS__]
#define FORE(xi, x) for(auto &xi : x)

const int MOD = (int)(1e9+7);
const int INF = 1<<30;
const ll LINF = 1LL<<60;
const vvi dirs = {{0,1},{0,-1},{-1,0},{1,0}};

template <typename T>
using maxpq = priority_queue<T>;
template <typename T>
using minpq = priority_queue<T, vector<T>, greater<T>>;
template <typename T>
using mset = multiset<T>;
template <typename T, typename U>
using mmap = multimap<T, U>;
template <typename T>
using iset = tree<T, null_type, less<T>, rb_tree_tag, tree_order_statistics_node_update>;

struct custom_hash {
    const uint64_t C = uint64_t(4e18 * acos(0)) + 71;
    const uint32_t RANDOM = chrono::steady_clock::now().time_since_epoch().count();
    size_t operator()(uint64_t x) const{
        return __builtin_bswap64((x^RANDOM)*C);
    }
};
template <typename T>
using uset = gp_hash_table<T, null_type, custom_hash>;
template <typename T, typename U>
using umap = gp_hash_table<T, U, custom_hash>;

template<typename T, typename U>
inline void amin(T &x, U y){if(y < x) x = y;}
template<typename T, typename U>
inline void amax(T &x, U y){if(y > x) x = y;}

int popcount(int x) {return __builtin_popcount(x);}
int popcount(ll x) {return __builtin_popcountll(x);}
int parity(int x) {return __builtin_parity(x);}
int parity(ll x) {return __builtin_parityll(x);}
int clz(int x) {return __builtin_clz(x);}
int clz(ll x) {return __builtin_clzll(x);}
int ctz(int x) {return __builtin_ctz(x);}
int ctz(ll x) {return __builtin_ctzll(x);}
void setBit(int &a, int b) { a |= int(1ULL<<b);}
void setBit(ll &a, int b) {a |= ll(1ULL<<b);}
void clearBit(int &a, int b) { a &= int(~(1ULL<<b));}
void clearBit(ll &a, int b) { a &= ll(~(1ULL<<b));}
void toggleBit(int &a, int b) { a ^= int(1ULL<<b);}
void toggleBit(ll &a, int b) { a ^= ll(1ULL<<b);}
int checkBit(int a, int b) {return !!(a & (1ULL<<b));}
int checkBit(ll a, int b) {return !!(a & (1ULL<<b));}

#define endl "\n"
#define spc " "
template<typename... Args>
void read(Args&... args){((cin >> args), ...);}
template<typename... Args>
void dec(Args&... args){(args--, ...);}
template<typename T>
void readvec(vec<T> &v, int n){v.resize(n); FOR(i, 0, n) cin >> v[i];}
template<typename T>
void readvec1(vec<T> &v, int n){v.resize(n+1); FOR(i, 1, n+1) cin >> v[i];}
template<typename T>
void print(vec<T> v){FOR(i, 0, sz(v)) cout << v[i] << " \n"[i==sz(v)-1];}
template<typename T>
void print1(vec<T> v){FOR(i, 1, sz(v)) cout << v[i] << " \n"[i==sz(v)-1];}
template<typename T>
void print(T a){cout << a << endl;}
template<typename T, typename... Args>
void print(T a, Args... args){cout << a << spc; print(args...);}

template<typename T>
void toBin(T a) {cerr << "[" << bitset<8*sizeof(a)>(a) << "]";}
void _cerr(int x, bool b=false) {cerr << x; if(b)toBin(x);}
void _cerr(long x, bool b=false) {cerr << x; if(b)toBin(x);}
void _cerr(long long x, bool b=false) {cerr << x; if(b)toBin(x);}
void _cerr(unsigned x, bool b=false) {cerr << x; if(b)toBin(x);}
void _cerr(unsigned long x, bool b=false) {cerr << x;}
void _cerr(unsigned long long x, bool b=false) {cerr << x;}
void _cerr(float x, bool b=false) {cerr << x;}
void _cerr(double x, bool b=false) {cerr << x;}
void _cerr(long double x, bool b=false) {cerr << x;}
void _cerr(char x, bool b=false) {cerr << "\'" << x << "\'"; if(b)toBin(x);}
void _cerr(const char *x, bool b=false) {cerr << "\"" << x << "\"";}
void _cerr(const string &x, bool b=false) {cerr << "\"" << x << "\"";}
void _cerr(bool x, bool b=false) {cerr << (x ? "true" : "false"); if(b)toBin(x);}
template<size_t N>
void _cerr(const bitset<N> &x, bool b=false) {FOR(i,0,N)cerr<<x[i]?1:0;}

template<typename T, typename U>
void _cerr(const pair<T, U> &x, bool b=false){cerr << "{";_cerr(x.F,b);cerr<<", ";_cerr(x.S,b);cerr<<"}";}
template<typename T>
void _cerr(const T &x, bool b=false){int f=0;cerr<<"{";FORE(i,x)cerr<<(f++?", ":""),_cerr(i,b);cerr<<"}";}
#ifndef ONLINE_JUDGE
#define debug(args...){string _s=#args;replace(_s.begin(),_s.end(),',',' '); \
stringstream _ss(_s);istream_iterator<string> _it(_ss);err(_it, args);}
#define debugbin(args...){string _s=#args;replace(_s.begin(),_s.end(),',',' '); \
stringstream _ss(_s);istream_iterator<string> _it(_ss);errbin(_it, args);}
#else
#define debug(args...) {}
#define debugbin(args...) {}
#define cerr if(false)cerr
#endif  //ONLINE_JUDGE
void err(istream_iterator<string> it) {cerr << endl;}
void errbin(istream_iterator<string> it) {cerr << endl;}
template<typename T, typename... Args>
void err(istream_iterator<string> it,T a,Args... args){cerr<<*it<<" = ";_cerr(a);cerr<<endl;err(++it,args...);}
template<typename T, typename... Args>
void errbin(istream_iterator<string> it,T a,Args... args){cerr<<*it<<" = ";_cerr(a,1);cerr<<endl;errbin(++it,args...);}
template<typename T>
void printd(T a){cerr << a << endl;}
void printd(){cerr << endl;}

void solve(){

}

int main(){
#ifndef ONLINE_JUDGE
    freopen("input.txt", "r", stdin);
    freopen("output.txt", "w", stdout);
    auto start = chrono::high_resolution_clock::now();
#endif  //ONLINE_JUDGE
    ios_base::sync_with_stdio(false);
    cin.tie(nullptr);

    // int t; cin >> t;
    // while(t--) solve();
    solve();

#ifndef ONLINE_JUDGE
    auto end = chrono::high_resolution_clock::now();
    auto duration = chrono::duration_cast<chrono::milliseconds>(end-start);
    cerr << endl << "Time: " << duration.count() << "ms" << endl;
#endif  //ONLINE_JUDGE
}