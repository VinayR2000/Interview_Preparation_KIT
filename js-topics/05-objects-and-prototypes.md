# Objects and Prototypes

## Objects

### Creating Objects
```javascript
// Object literal
const user = { name: 'John', age: 25 };

// Constructor function
function User(name, age) {
  this.name = name;
  this.age = age;
}
const user = new User('John', 25);

// Object.create (specify prototype)
const proto = { greet() { return `Hi, ${this.name}`; } };
const user = Object.create(proto);
user.name = 'John';

// Class (ES6)
class User {
  constructor(name, age) { this.name = name; this.age = age; }
}
```

### Object Methods
```javascript
Object.keys(obj)           // ['name', 'age'] - own enumerable keys
Object.values(obj)         // ['John', 25] - own enumerable values
Object.entries(obj)        // [['name','John'], ['age',25]]
Object.assign({}, obj)     // Shallow copy
Object.freeze(obj)         // Immutable (shallow)
Object.seal(obj)           // Can modify existing, can't add/delete
Object.defineProperty(obj, 'key', { writable: false })
Object.getPrototypeOf(obj) // Get prototype
obj.hasOwnProperty('name') // true (own, not inherited)
'name' in obj              // true (own OR inherited)
```

### Property Descriptors
```javascript
Object.defineProperty(user, 'name', {
  value: 'John',
  writable: false,       // Can't reassign
  enumerable: true,      // Shows in for...in, Object.keys
  configurable: false,   // Can't delete or reconfigure
});
```

---

## Prototypal Inheritance

### Prototype Chain
```javascript
const animal = { breathe() { return 'breathing'; } };
const dog = Object.create(animal);
dog.bark = function() { return 'woof'; };

dog.bark();      // 'woof' (own property)
dog.breathe();   // 'breathing' (from prototype)

// Lookup: dog → animal → Object.prototype → null
```

### `__proto__` vs `prototype`
```javascript
// Every OBJECT has __proto__ (link to its prototype)
// Every FUNCTION has prototype (used when called with new)

function Person(name) { this.name = name; }
Person.prototype.greet = function() { return `Hi, ${this.name}`; };

const p = new Person('John');
p.__proto__ === Person.prototype;  // true
Person.prototype.__proto__ === Object.prototype;  // true
Object.prototype.__proto__ === null;  // End of chain
```

### Prototype Chain Diagram
```
p (instance)
  └── __proto__ → Person.prototype { greet() }
                    └── __proto__ → Object.prototype { toString(), hasOwnProperty() }
                                      └── __proto__ → null
```

---

## Classes (ES6 Syntactic Sugar)

```javascript
class Animal {
  #heartRate = 60;  // Private field

  constructor(name) {
    this.name = name;
  }

  // Method (on prototype)
  speak() { return `${this.name} makes a sound`; }

  // Getter/Setter
  get info() { return `${this.name}`; }
  set info(value) { this.name = value; }

  // Static method (on class itself, not instances)
  static create(name) { return new Animal(name); }

  // Private method
  #getHeartRate() { return this.#heartRate; }
}

class Dog extends Animal {
  constructor(name, breed) {
    super(name);  // Must call super() first
    this.breed = breed;
  }

  speak() {
    return `${this.name} barks`;  // Override parent
  }
}

const dog = new Dog('Rex', 'Labrador');
dog.speak();  // "Rex barks"
dog instanceof Dog;     // true
dog instanceof Animal;  // true
```

---

## Object Comparison and Copying

```javascript
// Reference comparison
{} === {};  // false (different references)
const a = { x: 1 };
const b = a;
a === b;    // true (same reference)

// Shallow copy
const copy1 = { ...original };
const copy2 = Object.assign({}, original);

// Deep copy
const deep = structuredClone(original);  // Modern
const deep2 = JSON.parse(JSON.stringify(original));  // Old (loses functions, dates)
```

---

## Key Interview Questions

**Q: What is prototypal inheritance?**
> Objects inherit from other objects via the prototype chain. When you access a property, JS looks on the object first, then its prototype, then the prototype's prototype, until it finds it or hits `null`.

**Q: What's the difference between `__proto__` and `prototype`?**
> `prototype` is a property on constructor functions — it becomes the `__proto__` of instances created with `new`. `__proto__` is the actual link on every object pointing to its prototype.

**Q: How is a class different from a constructor function?**
> Classes are syntactic sugar over constructor functions + prototypes. Key differences: classes aren't hoisted, always strict mode, can't be called without `new`, support `extends`/`super` easily, and have private fields (`#`).

**Q: What does `Object.create(null)` do?**
> Creates an object with NO prototype (`__proto__` is null). It has no inherited methods (no `toString`, `hasOwnProperty`). Useful for pure dictionaries with no prototype pollution risk.

**Q: What's the difference between shallow and deep copy?**
> Shallow copy duplicates top-level properties but nested objects still share references. Deep copy recursively duplicates everything — nested objects are independent copies. Use `structuredClone()` for deep copy.
