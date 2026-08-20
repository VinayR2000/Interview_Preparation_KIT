# Object-Oriented JavaScript

## Four Pillars of OOP

### 1. Encapsulation
Bundling data and methods that operate on that data within one unit.

```javascript
class BankAccount {
  #balance = 0;  // Private field

  constructor(owner) {
    this.owner = owner;
  }

  deposit(amount) {
    if (amount <= 0) throw new Error('Invalid amount');
    this.#balance += amount;
  }

  withdraw(amount) {
    if (amount > this.#balance) throw new Error('Insufficient funds');
    this.#balance -= amount;
  }

  get balance() { return this.#balance; }  // Controlled access
}

const account = new BankAccount('John');
account.deposit(100);
account.balance;      // 100 (via getter)
account.#balance;     // SyntaxError (private!)
```

### 2. Inheritance
Creating new classes based on existing ones.

```javascript
class Animal {
  constructor(name) { this.name = name; }
  speak() { return `${this.name} makes a sound`; }
}

class Dog extends Animal {
  speak() { return `${this.name} barks`; }  // Override
  fetch() { return `${this.name} fetches!`; }  // New method
}

class Cat extends Animal {
  speak() { return `${this.name} meows`; }
}

const dog = new Dog('Rex');
dog.speak();   // "Rex barks"
dog instanceof Dog;     // true
dog instanceof Animal;  // true
```

### 3. Polymorphism
Same interface, different implementations.

```javascript
class Shape {
  area() { throw new Error('Must implement area()'); }
}

class Circle extends Shape {
  constructor(radius) { super(); this.radius = radius; }
  area() { return Math.PI * this.radius ** 2; }
}

class Rectangle extends Shape {
  constructor(w, h) { super(); this.width = w; this.height = h; }
  area() { return this.width * this.height; }
}

// Same interface, different behavior
const shapes = [new Circle(5), new Rectangle(3, 4)];
shapes.map(s => s.area());  // [78.54, 12]
```

### 4. Abstraction
Hide complex implementation, expose simple interface.

```javascript
class EmailService {
  // Public interface (simple)
  async send(to, subject, body) {
    const validated = this.#validate(to);
    const formatted = this.#format(subject, body);
    await this.#deliver(validated, formatted);
  }

  // Hidden complexity
  #validate(email) { /* complex validation */ }
  #format(subject, body) { /* template processing */ }
  #deliver(to, content) { /* SMTP connection logic */ }
}
```

---

## Constructor Functions (Pre-class)

```javascript
function Person(name, age) {
  this.name = name;
  this.age = age;
}

// Methods on prototype (shared across instances)
Person.prototype.greet = function() {
  return `Hi, I'm ${this.name}`;
};

const p = new Person('John', 25);
p.greet();  // "Hi, I'm John"
```

---

## Prototype Chain

```javascript
// Instance → Constructor.prototype → Object.prototype → null

const dog = new Dog('Rex');
dog.fetch();      // Found on Dog.prototype
dog.speak();      // Found on Animal.prototype (inherited)
dog.toString();   // Found on Object.prototype
dog.nonExist;     // undefined (reached null)
```

---

## Class Features

```javascript
class User {
  // Public field
  role = 'user';
  
  // Private field
  #password;
  
  // Static field
  static count = 0;
  
  constructor(name, password) {
    this.name = name;
    this.#password = password;
    User.count++;
  }
  
  // Instance method
  greet() { return `Hi, ${this.name}`; }
  
  // Static method (on class, not instances)
  static getCount() { return User.count; }
  
  // Getter
  get info() { return `${this.name} (${this.role})`; }
  
  // Setter
  set info(value) { this.name = value; }
  
  // Private method
  #hashPassword() { /* ... */ }
}
```

---

## Composition vs Inheritance

```javascript
// ❌ Inheritance hierarchy problems (gorilla-banana problem)
class Animal { }
class FlyingAnimal extends Animal { fly() {} }
class SwimmingAnimal extends Animal { swim() {} }
// Duck needs both! Can't extend multiple classes.

// ✅ Composition (mix behaviors)
const canFly = (obj) => ({ ...obj, fly: () => `${obj.name} flies` });
const canSwim = (obj) => ({ ...obj, swim: () => `${obj.name} swims` });
const canWalk = (obj) => ({ ...obj, walk: () => `${obj.name} walks` });

const duck = canWalk(canSwim(canFly({ name: 'Duck' })));
duck.fly();   // "Duck flies"
duck.swim();  // "Duck swims"
```

---

## Key Interview Questions

**Q: How does JS implement inheritance differently from Java/C++?**
> JS uses prototypal inheritance (objects inherit from objects via prototype chain). Java/C++ use classical inheritance (classes inherit from classes). ES6 `class` is syntactic sugar over prototypes.

**Q: What is the prototype chain?**
> When accessing a property on an object, JS looks: 1) Own properties. 2) `__proto__` (constructor's prototype). 3) Prototype's prototype. Up to `Object.prototype`. Then `null` (property undefined).

**Q: Composition vs Inheritance - when to use which?**
> Prefer composition ("has-a") over inheritance ("is-a"). Inheritance creates tight coupling and rigid hierarchies. Composition is more flexible — mix any behaviors without hierarchy constraints. Use inheritance only for clear "is-a" relationships.

**Q: What does `new` keyword do?**
> 1) Creates empty object. 2) Sets `__proto__` to constructor's prototype. 3) Executes constructor with `this` = new object. 4) Returns the object (unless constructor returns another object explicitly).
